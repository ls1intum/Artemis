package de.tum.cit.aet.artemis.videosource.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.domain.GocastApprovalAttempt;
import de.tum.cit.aet.artemis.videosource.domain.GocastApprovalAttemptStatus;
import de.tum.cit.aet.artemis.videosource.dto.GocastVerifiedCourseDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingConflictException;

class GocastConnectionRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final Instant NOW = Instant.parse("2026-09-05T03:00:00Z");

    private static final Instant EXPIRY = Instant.parse("2026-09-05T03:15:00Z");

    @Autowired
    private GocastConnectionRepository connectionRepository;

    @Autowired
    private GocastCourseBindingRepository bindingRepository;

    @Autowired
    private GocastApprovalAttemptRepository attemptRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Course course;

    @BeforeEach
    void setUp() {
        course = courseUtilService.addEmptyCourse();
    }

    @AfterEach
    void cleanUp() {
        attemptRepository.deleteAll();
        bindingRepository.deleteAll();
    }

    @Test
    void completesClaimedAttemptAndReturnsSavedResultForDuplicateCallback() {
        var attempt = connectionRepository.startAttempt(course.getId(), "state-hash", EXPIRY);
        assertThat(attemptRepository.findByCourseId(course.getId())).contains(attempt);
        assertThat(connectionRepository.attachRemoteRequest(course.getId(), "state-hash", "request-id", EXPIRY)).isTrue();
        assertThat(connectionRepository.claimAttempt("state-hash", "request-id", NOW)).get().extracting(a -> a.getStatus()).isEqualTo(GocastApprovalAttemptStatus.CLAIMED);

        var verified = verifiedCourse(37, 23);
        var binding = connectionRepository.completeAttempt("state-hash", "request-id", verified, NOW);
        var duplicate = connectionRepository.completeAttempt("state-hash", "request-id", verified, NOW);

        assertThat(duplicate.getId()).isEqualTo(binding.getId());
        assertThat(bindingRepository.findByCourseId(course.getId())).get().satisfies(saved -> {
            assertThat(saved.getGocastCourseId()).isEqualTo(37);
            assertThat(saved.getGocastGrantId()).isEqualTo(23);
        });
        assertThat(attemptRepository.findByCourseId(course.getId())).get().extracting(a -> a.getStatus()).isEqualTo(GocastApprovalAttemptStatus.COMPLETED);
    }

    @Test
    void restartInvalidatesClaimedOrCompletedAttemptAndLateCompletionCannotRestoreIt() {
        connectionRepository.startAttempt(course.getId(), "old-state", EXPIRY);
        connectionRepository.attachRemoteRequest(course.getId(), "old-state", "old-request", EXPIRY);
        connectionRepository.claimAttempt("old-state", "old-request", NOW);

        connectionRepository.startAttempt(course.getId(), "new-state", EXPIRY.plusSeconds(60));

        assertThatThrownBy(() -> connectionRepository.completeAttempt("old-state", "old-request", verifiedCourse(37, 23), NOW)).isInstanceOf(GocastBindingConflictException.class);
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
        assertThat(attemptRepository.findByCourseId(course.getId())).get().satisfies(current -> assertThat(current.getStateHash()).isEqualTo("new-state"));
    }

    @Test
    void activeBindingPreventsOverwriteAndRemoteCourseIsUnique() {
        complete(course, "first", "request-first", verifiedCourse(37, 23));
        assertThatThrownBy(() -> connectionRepository.startAttempt(course.getId(), "replacement", EXPIRY)).isInstanceOf(GocastBindingConflictException.class);

        Course secondCourse = courseUtilService.addEmptyCourse();
        connectionRepository.startAttempt(secondCourse.getId(), "second", EXPIRY);
        connectionRepository.attachRemoteRequest(secondCourse.getId(), "second", "request-second", EXPIRY);
        connectionRepository.claimAttempt("second", "request-second", NOW);

        assertThatThrownBy(() -> connectionRepository.completeAttempt("second", "request-second", verifiedCourse(37, 99), NOW)).isInstanceOf(GocastBindingConflictException.class);
        assertThat(bindingRepository.findByCourseId(secondCourse.getId())).isEmpty();
        assertThat(bindingRepository.findByCourseId(course.getId())).get().satisfies(saved -> assertThat(saved.getGocastGrantId()).isEqualTo(23));
    }

    @Test
    void unlinkInvalidatesPendingAttemptAndConditionalDeleteCannotRemoveReplacement() {
        connectionRepository.startAttempt(course.getId(), "old-state", EXPIRY);
        connectionRepository.attachRemoteRequest(course.getId(), "old-state", "old-request", EXPIRY);
        assertThat(connectionRepository.claimUnlink(course.getId())).isEmpty();
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();

        complete(course, "active", "request-active", verifiedCourse(37, 23));
        var claim = connectionRepository.claimUnlink(course.getId()).orElseThrow();
        assertThat(connectionRepository.completeUnlink(claim)).isTrue();
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
        assertThat(connectionRepository.completeUnlink(claim)).isFalse();
    }

    @Test
    void lateGrantStatusCannotReactivateRemovedBinding() {
        complete(course, "active", "request-active", verifiedCourse(37, 23));
        var snapshot = connectionRepository.getBindingSnapshot(course.getId()).orElseThrow();
        var unlink = connectionRepository.claimUnlink(course.getId()).orElseThrow();
        connectionRepository.completeUnlink(unlink);

        assertThat(connectionRepository.updateGrantStatus(snapshot,
                new de.tum.cit.aet.artemis.videosource.service.GocastConnectorService.GrantStatus(true, 23, 37, "algorithmen-üben", "Algorithmen & Datenstrukturen", "loggedin")))
                .isFalse();
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void unlinkClaimIsPersistedAndRetriedWithoutStatusRefreshChangingIt() {
        complete(course, "active", "request-active", verifiedCourse(37, 23));

        var firstClaim = connectionRepository.claimUnlink(course.getId()).orElseThrow();
        var retryClaim = connectionRepository.claimUnlink(course.getId()).orElseThrow();

        assertThat(retryClaim).isEqualTo(firstClaim);
        assertThat(bindingRepository.findByCourseId(course.getId())).get()
                .satisfies(binding -> assertThat(binding.getStatus()).isEqualTo(de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus.UNLINKING));
        assertThat(connectionRepository.updateGrantStatus(firstClaim,
                new de.tum.cit.aet.artemis.videosource.service.GocastConnectorService.GrantStatus(true, 23, 37, "new-slug", "New name", "public"))).isFalse();
        assertThat(connectionRepository.completeUnlink(retryClaim)).isTrue();
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void completionRechecksExpiry() {
        prepareClaim(course, "state", "request");

        assertThatThrownBy(() -> connectionRepository.completeAttempt("state", "request", verifiedCourse(37, 23), EXPIRY)).isInstanceOf(GocastBindingConflictException.class);
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void claimRejectsAnAttemptAtItsExactExpiry() {
        connectionRepository.startAttempt(course.getId(), "state", EXPIRY);
        connectionRepository.attachRemoteRequest(course.getId(), "state", "request", EXPIRY);

        assertThat(connectionRepository.claimAttempt("state", "request", EXPIRY)).isEmpty();
        assertThat(attemptRepository.findByCourseId(course.getId())).get().extracting(GocastApprovalAttempt::getStatus).isEqualTo(GocastApprovalAttemptStatus.EXPIRED);
    }

    @Test
    void courseLockSerializesInitialTransitions() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch transitionStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            courseRepository.findByIdWithPessimisticWrite(course.getId()).orElseThrow();
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<?> waiting = CompletableFuture.supplyAsync(() -> {
            transitionStarted.countDown();
            return connectionRepository.startAttempt(course.getId(), "waiting-state", EXPIRY);
        });
        try {
            assertThat(transitionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(waiting).isNotDone();
        }
        finally {
            release.countDown();
        }
        holder.get(5, TimeUnit.SECONDS);
        waiting.get(5, TimeUnit.SECONDS);
        assertThat(attemptRepository.findByCourseId(course.getId())).isPresent();
    }

    @Test
    void parallelStartsLeaveOneCurrentAttempt() {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.startAttempt(course.getId(), "parallel-a", EXPIRY)));
        var second = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.startAttempt(course.getId(), "parallel-b", EXPIRY)));
        awaitReadyAndRelease(ready, go);

        assertThat(first.join()).isNull();
        assertThat(second.join()).isNull();
        assertThat(attemptRepository.findAll()).singleElement().extracting(a -> a.getStateHash()).isIn("parallel-a", "parallel-b");
    }

    @Test
    void completeAndRestartSerializeWithoutRestoringAnOldAttempt() {
        connectionRepository.startAttempt(course.getId(), "old-state", EXPIRY);
        connectionRepository.attachRemoteRequest(course.getId(), "old-state", "old-request", EXPIRY);
        connectionRepository.claimAttempt("old-state", "old-request", NOW);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var complete = CompletableFuture
                .supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.completeAttempt("old-state", "old-request", verifiedCourse(37, 23), NOW)));
        var restart = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.startAttempt(course.getId(), "new-state", EXPIRY)));
        awaitReadyAndRelease(ready, go);

        assertThat(successCount(complete.join(), restart.join())).isEqualTo(1);
        if (bindingRepository.findByCourseId(course.getId()).isPresent()) {
            assertThat(attemptRepository.findByCourseId(course.getId())).get().extracting(a -> a.getStateHash()).isEqualTo("old-state");
        }
        else {
            assertThat(attemptRepository.findByCourseId(course.getId())).get().extracting(a -> a.getStateHash()).isEqualTo("new-state");
        }
    }

    @Test
    void unlinkAndCompleteCannotLeaveARecreatedBinding() {
        connectionRepository.startAttempt(course.getId(), "state", EXPIRY);
        connectionRepository.attachRemoteRequest(course.getId(), "state", "request", EXPIRY);
        connectionRepository.claimAttempt("state", "request", NOW);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var complete = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.completeAttempt("state", "request", verifiedCourse(37, 23), NOW)));
        var unlink = CompletableFuture
                .supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.claimUnlink(course.getId()).ifPresent(connectionRepository::completeUnlink)));
        awaitReadyAndRelease(ready, go);

        complete.join();
        unlink.join();
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void simultaneousCompletionsEnforceRemoteCourseUniqueness() {
        Course secondCourse = courseUtilService.addEmptyCourse();
        prepareClaim(course, "first-state", "first-request");
        prepareClaim(secondCourse, "second-state", "second-request");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var first = CompletableFuture
                .supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.completeAttempt("first-state", "first-request", verifiedCourse(37, 23), NOW)));
        var second = CompletableFuture
                .supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.completeAttempt("second-state", "second-request", verifiedCourse(37, 99), NOW)));
        awaitReadyAndRelease(ready, go);

        assertThat(successCount(first.join(), second.join())).isEqualTo(1);
        assertThat(bindingRepository.findAll()).singleElement().satisfies(binding -> assertThat(binding.getGocastCourseId()).isEqualTo(37));
    }

    @Test
    void unrelatedIntegrityFailureIsNotReportedAsRemoteCourseConflict() {
        prepareClaim(course, "state", "request");
        var invalidInternalResult = new GocastVerifiedCourseDTO(17, 23, 37, null, "Course", "loggedin");

        assertThatThrownBy(() -> connectionRepository.completeAttempt("state", "request", invalidInternalResult, NOW)).isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(GocastBindingConflictException.class);
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    private void complete(Course target, String state, String requestId, GocastVerifiedCourseDTO verified) {
        prepareClaim(target, state, requestId);
        connectionRepository.completeAttempt(state, requestId, verified, NOW);
    }

    private void prepareClaim(Course target, String state, String requestId) {
        connectionRepository.startAttempt(target.getId(), state, EXPIRY);
        connectionRepository.attachRemoteRequest(target.getId(), state, requestId, EXPIRY);
        connectionRepository.claimAttempt(state, requestId, NOW);
    }

    private static GocastVerifiedCourseDTO verifiedCourse(long gocastCourseId, long grantId) {
        return new GocastVerifiedCourseDTO(17, grantId, gocastCourseId, "algorithmen-üben", "Algorithmen & Datenstrukturen", "loggedin");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for lock release");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static Throwable runTogether(CountDownLatch ready, CountDownLatch go, Runnable operation) {
        ready.countDown();
        await(go);
        try {
            operation.run();
            return null;
        }
        catch (Throwable throwable) {
            return throwable;
        }
    }

    private static void awaitReadyAndRelease(CountDownLatch ready, CountDownLatch go) {
        await(ready);
        go.countDown();
    }

    private static int successCount(Throwable... results) {
        AtomicInteger successes = new AtomicInteger();
        for (Throwable result : results) {
            if (result == null) {
                successes.incrementAndGet();
            }
            else {
                assertThat(result).isInstanceOf(GocastBindingConflictException.class);
            }
        }
        return successes.get();
    }
}
