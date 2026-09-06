package de.tum.cit.aet.artemis.videosource.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void completesUsableAttemptAndDeletesItsCorrelation() {
        var attempt = connectionRepository.startAttempt(course.getId(), "state-hash", 17, EXPIRY);
        assertThat(attemptRepository.findByCourseId(course.getId())).contains(attempt);
        assertThat(connectionRepository.findUsableAttempt("state-hash", NOW)).get().satisfies(claim -> assertThat(claim.integrationId()).isEqualTo(17));

        var verified = verifiedCourse(37, 23);
        var binding = connectionRepository.completeAttempt("state-hash", verified, NOW);

        assertThat(bindingRepository.findByCourseId(course.getId())).get().satisfies(saved -> {
            assertThat(saved.getId()).isEqualTo(binding.getId());
            assertThat(saved.getGocastCourseId()).isEqualTo(37);
            assertThat(saved.getGocastGrantId()).isEqualTo(23);
        });
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
        assertThatThrownBy(() -> connectionRepository.completeAttempt("state-hash", verified, NOW)).isInstanceOf(GocastBindingConflictException.class);
    }

    @Test
    void restartInvalidatesPendingAttemptAndLateCompletionCannotRestoreIt() {
        connectionRepository.startAttempt(course.getId(), "old-state", 17, EXPIRY);

        connectionRepository.startAttempt(course.getId(), "new-state", 17, EXPIRY.plusSeconds(60));

        assertThatThrownBy(() -> connectionRepository.completeAttempt("old-state", verifiedCourse(37, 23), NOW)).isInstanceOf(GocastBindingConflictException.class);
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
        assertThat(attemptRepository.findByCourseId(course.getId())).get().satisfies(current -> assertThat(current.getStateHash()).isEqualTo("new-state"));
    }

    @Test
    void activeBindingPreventsOverwriteAndRemoteCourseIsUnique() {
        complete(course, "first", verifiedCourse(37, 23));
        assertThatThrownBy(() -> connectionRepository.startAttempt(course.getId(), "replacement", 17, EXPIRY)).isInstanceOf(GocastBindingConflictException.class);

        Course secondCourse = courseUtilService.addEmptyCourse();
        connectionRepository.startAttempt(secondCourse.getId(), "second", 17, EXPIRY);

        assertThatThrownBy(() -> connectionRepository.completeAttempt("second", verifiedCourse(37, 99), NOW)).isInstanceOf(GocastBindingConflictException.class);
        assertThat(bindingRepository.findByCourseId(secondCourse.getId())).isEmpty();
        assertThat(bindingRepository.findByCourseId(course.getId())).get().satisfies(saved -> assertThat(saved.getGocastGrantId()).isEqualTo(23));
    }

    @Test
    void unlinkInvalidatesPendingAttemptAndConditionalDeleteCannotRemoveReplacement() {
        connectionRepository.startAttempt(course.getId(), "old-state", 17, EXPIRY);
        assertThat(connectionRepository.prepareUnlink(course.getId())).isEmpty();
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
        assertThatThrownBy(() -> connectionRepository.completeAttempt("old-state", verifiedCourse(37, 23), NOW)).isInstanceOf(GocastBindingConflictException.class);
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();

        complete(course, "active", verifiedCourse(37, 23));
        var claim = connectionRepository.prepareUnlink(course.getId()).orElseThrow();
        assertThat(connectionRepository.completeUnlink(claim)).isTrue();
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();

        complete(course, "replacement", verifiedCourse(41, 29));

        assertThat(connectionRepository.completeUnlink(claim)).isFalse();
        assertThat(bindingRepository.findByCourseId(course.getId())).get().satisfies(replacement -> {
            assertThat(replacement.getGocastCourseId()).isEqualTo(41);
            assertThat(replacement.getGocastGrantId()).isEqualTo(29);
        });
    }

    @Test
    void lateGrantStatusCannotReactivateRemovedBinding() {
        complete(course, "active", verifiedCourse(37, 23));
        var snapshot = connectionRepository.getBindingSnapshot(course.getId()).orElseThrow();
        var unlink = connectionRepository.prepareUnlink(course.getId()).orElseThrow();
        connectionRepository.completeUnlink(unlink);

        assertThat(connectionRepository.updateGrantMetadata(snapshot,
                new de.tum.cit.aet.artemis.videosource.service.GocastConnectorService.GrantDetails(37, "algorithmen-üben", "Algorithmen & Datenstrukturen", "loggedin"))).isFalse();
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void metadataRefreshDoesNotBlockExactGrantDelete() {
        complete(course, "active", verifiedCourse(37, 23));

        var unlink = connectionRepository.prepareUnlink(course.getId()).orElseThrow();

        assertThat(connectionRepository.updateGrantMetadata(unlink,
                new de.tum.cit.aet.artemis.videosource.service.GocastConnectorService.GrantDetails(37, "new-slug", "New name", "public"))).isTrue();
        assertThat(bindingRepository.findByCourseId(course.getId())).get().satisfies(binding -> assertThat(binding.getVersion()).isGreaterThan(unlink.version()));
        assertThat(connectionRepository.completeUnlink(unlink)).isTrue();
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
        assertThat(connectionRepository.completeUnlink(unlink)).isTrue();
    }

    @Test
    void staleActiveStatusCannotResurrectARevokedBinding() {
        complete(course, "active", verifiedCourse(37, 23));
        var staleActive = connectionRepository.getBindingSnapshot(course.getId()).orElseThrow();

        assertThat(connectionRepository.markGrantRevoked(staleActive)).isTrue();
        assertThat(connectionRepository.updateGrantMetadata(staleActive,
                new de.tum.cit.aet.artemis.videosource.service.GocastConnectorService.GrantDetails(37, "new-slug", "New name", "public"))).isFalse();

        assertThat(bindingRepository.findByCourseId(course.getId())).get().extracting(binding -> binding.getStatus()).isEqualTo(GocastBindingStatus.REVOKED);
    }

    @Test
    void completionRechecksExpiry() {
        prepareAttempt(course, "state");

        assertThatThrownBy(() -> connectionRepository.completeAttempt("state", verifiedCourse(37, 23), EXPIRY)).isInstanceOf(GocastBindingConflictException.class);
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void usableLookupRejectsAnAttemptAtItsExactExpiryWithoutPersistingDerivedState() {
        connectionRepository.startAttempt(course.getId(), "state", 17, EXPIRY);

        assertThat(connectionRepository.findUsableAttempt("state", EXPIRY)).isEmpty();
        assertThat(attemptRepository.findByCourseId(course.getId())).isPresent();
    }

    @Test
    void cancelAttemptDeletesOnlyTheMatchingCurrentAttempt() {
        connectionRepository.startAttempt(course.getId(), "old-state", 17, EXPIRY);
        connectionRepository.startAttempt(course.getId(), "new-state", 17, EXPIRY);

        connectionRepository.cancelAttempt("old-state");

        assertThat(attemptRepository.findByCourseId(course.getId())).get().satisfies(attempt -> assertThat(attempt.getStateHash()).isEqualTo("new-state"));
        connectionRepository.cancelAttempt("new-state");
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void courseLockSerializesInitialTransitions() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch transitionStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AtomicInteger waitingSessionId = new AtomicInteger();
        String databaseProductName = jdbcTemplate.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            courseRepository.findByIdWithPessimisticWrite(course.getId()).orElseThrow();
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<?> waiting = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
            waitingSessionId.set(currentDatabaseSessionId(databaseProductName));
            transitionStarted.countDown();
            return connectionRepository.startAttempt(course.getId(), "waiting-state", 17, EXPIRY);
        }));
        try {
            assertThat(transitionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(25)).untilAsserted(
                    () -> assertThat(blockedTransitionCount(databaseProductName, waitingSessionId.get())).as("the transition's database session is blocked").isGreaterThan(0));
            assertThat(waiting).as("the transition remains blocked until the course lock is released").isNotDone();
        }
        finally {
            release.countDown();
        }
        holder.get(5, TimeUnit.SECONDS);
        waiting.get(5, TimeUnit.SECONDS);
        assertThat(attemptRepository.findByCourseId(course.getId())).isPresent();
    }

    private int currentDatabaseSessionId(String databaseProductName) {
        return switch (databaseProductName) {
            case "PostgreSQL" -> jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
            case "MySQL" -> jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Integer.class);
            default -> throw new AssertionError("Unsupported test database: " + databaseProductName);
        };
    }

    private int blockedTransitionCount(String databaseProductName, int sessionId) {
        return switch (databaseProductName) {
            case "PostgreSQL" -> jdbcTemplate.queryForObject("SELECT cardinality(pg_blocking_pids(?))", Integer.class, sessionId);
            case "MySQL" -> jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.PROCESSLIST
                    WHERE ID = ? AND LOWER(INFO) LIKE '%for update%'
                    """, Integer.class, sessionId);
            default -> throw new AssertionError("Unsupported test database: " + databaseProductName);
        };
    }

    @Test
    void parallelStartsLeaveOneCurrentAttempt() {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.startAttempt(course.getId(), "parallel-a", 17, EXPIRY)));
        var second = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.startAttempt(course.getId(), "parallel-b", 17, EXPIRY)));
        awaitReadyAndRelease(ready, go);

        assertThat(first.join()).isNull();
        assertThat(second.join()).isNull();
        assertThat(attemptRepository.findAll()).singleElement().extracting(a -> a.getStateHash()).isIn("parallel-a", "parallel-b");
    }

    @Test
    void completeAndRestartSerializeWithoutRestoringAnOldAttempt() {
        connectionRepository.startAttempt(course.getId(), "old-state", 17, EXPIRY);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var complete = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.completeAttempt("old-state", verifiedCourse(37, 23), NOW)));
        var restart = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.startAttempt(course.getId(), "new-state", 17, EXPIRY)));
        awaitReadyAndRelease(ready, go);

        assertThat(successCount(complete.join(), restart.join())).isEqualTo(1);
        if (bindingRepository.findByCourseId(course.getId()).isPresent()) {
            assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
        }
        else {
            assertThat(attemptRepository.findByCourseId(course.getId())).get().extracting(a -> a.getStateHash()).isEqualTo("new-state");
        }
    }

    @Test
    void unlinkAndCompleteCannotLeaveARecreatedBinding() {
        connectionRepository.startAttempt(course.getId(), "state", 17, EXPIRY);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var complete = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.completeAttempt("state", verifiedCourse(37, 23), NOW)));
        var unlink = CompletableFuture
                .supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.prepareUnlink(course.getId()).ifPresent(connectionRepository::completeUnlink)));
        awaitReadyAndRelease(ready, go);

        Throwable completeResult = complete.join();
        Throwable unlinkResult = unlink.join();
        assertThat(unlinkResult).isNull();
        if (completeResult != null) {
            assertThat(completeResult).isInstanceOf(GocastBindingConflictException.class);
        }
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
        assertThat(attemptRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    void simultaneousCompletionsEnforceRemoteCourseUniqueness() {
        Course secondCourse = courseUtilService.addEmptyCourse();
        prepareAttempt(course, "first-state");
        prepareAttempt(secondCourse, "second-state");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.completeAttempt("first-state", verifiedCourse(37, 23), NOW)));
        var second = CompletableFuture.supplyAsync(() -> runTogether(ready, go, () -> connectionRepository.completeAttempt("second-state", verifiedCourse(37, 99), NOW)));
        awaitReadyAndRelease(ready, go);

        assertThat(successCount(first.join(), second.join())).isEqualTo(1);
        assertThat(bindingRepository.findAll()).singleElement().satisfies(binding -> assertThat(binding.getGocastCourseId()).isEqualTo(37));
    }

    @Test
    void unrelatedIntegrityFailureIsNotReportedAsRemoteCourseConflict() {
        prepareAttempt(course, "state");
        var invalidInternalResult = new GocastVerifiedCourseDTO(17, 23, 37, null, "Course", "loggedin");

        assertThatThrownBy(() -> connectionRepository.completeAttempt("state", invalidInternalResult, NOW)).isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(GocastBindingConflictException.class);
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    private void complete(Course target, String state, GocastVerifiedCourseDTO verified) {
        prepareAttempt(target, state);
        connectionRepository.completeAttempt(state, verified, NOW);
    }

    private void prepareAttempt(Course target, String state) {
        connectionRepository.startAttempt(target.getId(), state, 17, EXPIRY);
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
