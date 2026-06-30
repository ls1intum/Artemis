package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
    void secondReservationForSamePairWhilePendingIsSkipped() {
        long courseId = 9001L;
        long userId = 9101L;
        long exerciseId = 9201L;

        Optional<String> first = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null);
        assertThat(first).isPresent();

        Optional<String> second = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null);
        assertThat(second).as("a second run for the same (user, exercise) must be skipped while the first is pending").isEmpty();
    }

    @Test
    void releasingTheMarkerFreesThePairButRemoveJobAloneDoesNot() {
        long courseId = 9002L;
        long userId = 9102L;
        long exerciseId = 9202L;

        String tokenA = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null).orElseThrow();

        // removeJob is job-map-only and deliberately does NOT free the in-flight marker, so the pair stays reserved.
        pyrisJobService.removeJob(pyrisJobService.getJob(tokenA));
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null))
                .as("removeJob must not free the in-flight marker; the pair must remain reserved").isEmpty();

        // Explicitly releasing the marker frees the pair for a new reservation.
        pyrisJobService.releaseStruggleInFlightMarker(tokenA, userId, exerciseId);
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null))
                .as("after releasing the marker the pair can be reserved again").isPresent();
    }

    @Test
    void staleReleaseDoesNotClearANewerReservation() {
        long courseId = 9003L;
        long userId = 9103L;
        long exerciseId = 9203L;

        // Reserve token A and fully release it (job + marker).
        String tokenA = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null).orElseThrow();
        pyrisJobService.releaseStruggleInFlightJob(tokenA, userId, exerciseId);

        // Reserve token B for the same pair.
        String tokenB = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null).orElseThrow();
        assertThat(tokenB).isNotEqualTo(tokenA);

        // A late/duplicate release carrying the stale token A must NOT clear B's reservation (token-conditional).
        pyrisJobService.releaseStruggleInFlightJob(tokenA, userId, exerciseId);

        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, null, null, null, null))
                .as("a stale release for token A must not free token B's still-pending reservation").isEmpty();
    }

    @Test
    void jobCarriesIntentEpisodeIdConfirmReasonAndRequestToken() {
        long courseId = 9004L;
        long userId = 9104L;
        long exerciseId = 9204L;

        String token = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-42", "progress", "rt-uuid").orElseThrow();
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

        String token = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-1", null, "tok-A").orElseThrow();

        // A matching token removes the job and frees the in-flight marker, so a new reservation can be taken.
        pyrisJobService.removeStruggleJobIfTokenMatches(userId, exerciseId, "tok-A");
        assertThat(pyrisJobService.getJob(token)).as("the scoped cancel must remove the job entry").isNull();
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-2", null, "tok-B"))
                .as("after a matching scoped cancel the pair can be reserved again").isPresent();
    }

    @Test
    void scopedCancelNonMatchingTokenLeavesJobIntact() {
        long courseId = 9006L;
        long userId = 9106L;
        long exerciseId = 9206L;

        String token = pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-1", null, "tok-A").orElseThrow();

        // A non-matching token must be a noop: the scoped-cancel guarantee means cancel(A) never removes a since-started B.
        pyrisJobService.removeStruggleJobIfTokenMatches(userId, exerciseId, "tok-OTHER");
        assertThat(pyrisJobService.getJob(token)).as("a non-matching token must leave the job intact").isNotNull();
        assertThat(pyrisJobService.addStruggleInterventionJobIfNonePending(courseId, userId, exerciseId, "decide", "ep-2", null, "tok-B"))
                .as("a non-matching scoped cancel must leave the in-flight reservation intact").isEmpty();
    }

    @Test
    void scopedCancelNoPendingJobIsNoop() {
        long userId = 9107L;
        long exerciseId = 9207L;

        // No reservation for this pair: a scoped cancel is an idempotent noop (no exception).
        pyrisJobService.removeStruggleJobIfTokenMatches(userId, exerciseId, "tok-A");
    }
}
