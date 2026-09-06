package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;

/**
 * Integration test for the cluster-atomic single-flight guard on {@link PyrisJobService}. Runs against a real
 * Hazelcast instance (wired by the Spring test context) because the guard's correctness lives entirely in
 * Hazelcast's {@code putIfAbsent(ttl)} reservation + token-conditional {@code remove(key, value)} semantics; a
 * mocked map would not exercise them. Each test uses distinct {@code (courseId, userId, exerciseId)} longs so the
 * shared cluster map cannot cross-contaminate.
 */
class PyrisJobServiceStruggleTest extends AbstractIrisIntegrationTest {

    @Autowired
    private PyrisJobService pyrisJobService;

    @Test
    void keepAliveRefreshExtendsTheMarkerSoALongRunKeepsItsSlot() {
        long courseId = 9010L;
        long userId = 9110L;
        long exerciseId = 9210L;

        // The reservation passes jobTimeout explicitly as a per-entry TTL, so shrinking the map config alone would
        // change nothing; shrink the field the reservation actually reads. Without the refresh this reproduces:
        // the marker expires while updateJob keeps the job alive, and the pair becomes reservable mid-run.
        Object previousTimeout = ReflectionTestUtils.getField(pyrisJobService, "jobTimeout");
        // Both maps are built lazily from the jobTimeout field and keep the default entry TTL they were born with.
        // Touching them here, while the field still holds the configured value, keeps this test from leaving a
        // two-second TTL behind for every later test that shares this Spring context. Removing entries that were
        // never added is a noop; the sentinels only have to be unmistakably this warm-up's.
        pyrisJobService.releaseStruggleInFlightJob("__test-map-warmup__", Long.MIN_VALUE, Long.MIN_VALUE);
        ReflectionTestUtils.setField(pyrisJobService, "jobTimeout", 2);
        try {
            String token = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null).orElseThrow();

            // Keep the run alive across more than one TTL the way a stream of non-terminal callbacks does.
            for (int i = 0; i < 8; i++) {
                var job = pyrisJobService.getJob(token);
                assertThat(job).as("the job must stay alive while it is being refreshed").isNotNull();
                pyrisJobService.updateJob(job);
                pyrisJobService.refreshStruggleInFlightMarker(token, userId, exerciseId);
                await().pollDelay(Duration.ofMillis(400)).atMost(Duration.ofSeconds(2)).until(() -> true);
            }

            assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null))
                    .as("the pair must stay reserved for the whole run, otherwise a second trigger duplicates the session and bubble").isEmpty();
        }
        finally {
            ReflectionTestUtils.setField(pyrisJobService, "jobTimeout", previousTimeout);
        }
    }

    @Test
    void staleMarkerRefreshCannotResurrectANewerReservation() {
        long courseId = 9011L;
        long userId = 9111L;
        long exerciseId = 9211L;

        String stale = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null).orElseThrow();
        pyrisJobService.releaseStruggleInFlightMarker(stale, userId, exerciseId);
        String current = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null).orElseThrow();

        // A late keep-alive from the finished run must not extend, or re-take, the slot the newer run now holds.
        pyrisJobService.refreshStruggleInFlightMarker(stale, userId, exerciseId);

        pyrisJobService.releaseStruggleInFlightMarker(current, userId, exerciseId);
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null))
                .as("releasing the current holder must free the pair; a stale refresh must not have kept it alive").isPresent();
    }

    @Test
    void secondReservationForSamePairWhilePendingIsSkipped() {
        long courseId = 9001L;
        long userId = 9101L;
        long exerciseId = 9201L;

        Optional<String> first = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null);
        assertThat(first).isPresent();

        Optional<String> second = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null);
        assertThat(second).as("a second run for the same (user, exercise) must be skipped while the first is pending").isEmpty();
    }

    @Test
    void releasingTheMarkerFreesThePairButRemoveJobAloneDoesNot() {
        long courseId = 9002L;
        long userId = 9102L;
        long exerciseId = 9202L;

        String tokenA = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null).orElseThrow();

        // removeJob is job-map-only and deliberately does NOT free the in-flight marker, so the pair stays reserved.
        pyrisJobService.removeJob(pyrisJobService.getJob(tokenA));
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null))
                .as("removeJob must not free the in-flight marker; the pair must remain reserved").isEmpty();

        // Explicitly releasing the marker frees the pair for a new reservation.
        pyrisJobService.releaseStruggleInFlightMarker(tokenA, userId, exerciseId);
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null))
                .as("after releasing the marker the pair can be reserved again").isPresent();
    }

    @Test
    void staleReleaseDoesNotClearANewerReservation() {
        long courseId = 9003L;
        long userId = 9103L;
        long exerciseId = 9203L;

        // Reserve token A and fully release it (job + marker).
        String tokenA = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null).orElseThrow();
        pyrisJobService.releaseStruggleInFlightJob(tokenA, userId, exerciseId);

        // Reserve token B for the same pair.
        String tokenB = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null).orElseThrow();
        assertThat(tokenB).isNotEqualTo(tokenA);

        // A late/duplicate release carrying the stale token A must NOT clear B's reservation (token-conditional).
        pyrisJobService.releaseStruggleInFlightJob(tokenA, userId, exerciseId);

        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null, null))
                .as("a stale release for token A must not free token B's still-pending reservation").isEmpty();
    }

    @Test
    void jobCarriesIntentEpisodeIdConfirmReasonAndRequestToken() {
        long courseId = 9004L;
        long userId = 9104L;
        long exerciseId = 9204L;

        String token = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-42", "progress", "rt-uuid", null).orElseThrow();
        var job = (StruggleInterventionJob) pyrisJobService.getJob(token);
        assertThat(job.intent()).isEqualTo("decide");
        assertThat(job.episodeId()).isEqualTo("ep-42");
        assertThat(job.confirmReason()).isEqualTo("progress");
        assertThat(job.requestToken()).isEqualTo("rt-uuid");
    }

    @Test
    void scopedCancelMatchingTokenRemovesJobAndFreesSlot() {
        long courseId = 9005L;
        long userId = 9105L;
        long exerciseId = 9205L;

        String token = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-1", null, "tok-A", null).orElseThrow();

        // A matching token removes the job and frees the in-flight marker, so a new reservation can be taken.
        pyrisJobService.removeStruggleJobIfTokenMatches(userId, exerciseId, "tok-A");
        assertThat(pyrisJobService.getJob(token)).as("the scoped cancel must remove the job entry").isNull();
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-2", null, "tok-B", null))
                .as("after a matching scoped cancel the pair can be reserved again").isPresent();
    }

    @Test
    void scopedCancelNonMatchingTokenLeavesJobIntact() {
        long courseId = 9006L;
        long userId = 9106L;
        long exerciseId = 9206L;

        String token = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-1", null, "tok-A", null).orElseThrow();

        // A non-matching token must be a noop: the scoped-cancel guarantee means cancel(A) never removes a since-started B.
        pyrisJobService.removeStruggleJobIfTokenMatches(userId, exerciseId, "tok-OTHER");
        assertThat(pyrisJobService.getJob(token)).as("a non-matching token must leave the job intact").isNotNull();
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-2", null, "tok-B", null))
                .as("a non-matching scoped cancel must leave the in-flight reservation intact").isEmpty();
    }

    @Test
    void scopedCancelSerializesUnderTheJobLockAgainstTheTerminalCallback() throws Exception {
        long courseId = 9008L;
        long userId = 9108L;
        long exerciseId = 9208L;

        String token = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-1", null, "tok-A", null).orElseThrow();

        // Stand in for the terminal callback, which runs its whole remove-job / handleDecision / release-marker
        // sequence under runWithJobLock(jobId). Hold that exact lock so the scoped cancel below must wait for it.
        var lockHeld = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);
        var cancelReturned = new AtomicBoolean(false);

        var callbackHolder = new Thread(() -> pyrisJobService.runWithJobLock(token, () -> {
            lockHeld.countDown();
            try {
                releaseLock.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        callbackHolder.start();
        assertThat(lockHeld.await(5, TimeUnit.SECONDS)).as("the callback stand-in must acquire the job lock").isTrue();

        // Signalled at the call site so the assertion below cannot pass merely because the canceller thread had not
        // been scheduled yet: we wait for the thread to be inside (or about to enter) removeStruggleJobIfTokenMatches
        // before judging whether it blocked.
        var cancelAtCallSite = new CountDownLatch(1);
        var canceller = new Thread(() -> {
            cancelAtCallSite.countDown();
            pyrisJobService.removeStruggleJobIfTokenMatches(userId, exerciseId, "tok-A");
            cancelReturned.set(true);
        });
        canceller.start();
        assertThat(cancelAtCallSite.await(5, TimeUnit.SECONDS)).as("the canceller thread must reach the scoped-cancel call").isTrue();

        // The canceller has entered the method while the callback still holds the job lock. It must not have
        // completed: it now serializes under that same lock instead of releasing the marker out from under an
        // in-flight decision. Without the fix the cancel takes no lock and its pure in-memory map ops return at once.
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(1)).until(() -> true);
        assertThat(cancelReturned).as("the scoped cancel must block until the terminal callback releases the job lock").isFalse();

        releaseLock.countDown();
        callbackHolder.join(5000);
        canceller.join(5000);
        assertThat(cancelReturned).as("once the job lock is free the scoped cancel completes").isTrue();

        assertThat(pyrisJobService.getJob(token)).as("after it runs, the matching scoped cancel has removed the job entry").isNull();
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-2", null, "tok-B", null))
                .as("and freed the pair for a new reservation").isPresent();
    }

    @Test
    void scopedCancelNoPendingJobIsNoop() {
        long userId = 9107L;
        long exerciseId = 9207L;

        // No reservation for this pair: a scoped cancel is an idempotent noop. Assert both halves of that claim -
        // it must not throw, and it must leave the pair reservable - rather than relying on the absence of an
        // exception, which would also "pass" if the call silently took the slot.
        assertThatCode(() -> pyrisJobService.removeStruggleJobIfTokenMatches(userId, exerciseId, "tok-A")).doesNotThrowAnyException();
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(9007L, userId, exerciseId, null, null, null, null, null))
                .as("a noop cancel must not have consumed the slot").isPresent();
    }
}
