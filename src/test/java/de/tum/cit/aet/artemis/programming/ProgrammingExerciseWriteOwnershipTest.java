package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseWriteOwnershipService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseWriteOwnershipTestRepository;

class ProgrammingExerciseWriteOwnershipTest extends AbstractProgrammingIntegrationIndependentTest {

    @Autowired
    private ProgrammingExerciseWriteOwnershipTestRepository ownershipRepository;

    @Autowired
    private ProgrammingExerciseWriteOwnershipService ownershipService;

    @Autowired
    private DataSource dataSource;

    private ProgrammingExercise exercise;

    @BeforeEach
    void createExercise() {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = (ProgrammingExercise) course.getExercises().iterator().next();
    }

    @Test
    void exactTokenReleaseCannotClearAReplacementOwner() {
        String first = ownershipService.claim(exercise.getId(), "persist");
        assertThat(ownershipService.get(exercise.getId())).hasValueSatisfying(owner -> {
            assertThat(owner.token()).isEqualTo(first);
            assertThat(owner.purpose()).isEqualTo("persist");
            assertThat(owner.owner()).isNotBlank();
            assertThat(owner.startedAt()).isBeforeOrEqualTo(Instant.now());
        });
        assertThatThrownBy(() -> ownershipService.claim(exercise.getId(), "edit")).isInstanceOf(ConflictException.class);
        assertThat(ownershipService.release(exercise.getId(), UUID.randomUUID().toString())).isFalse();
        assertThat(ownershipService.release(exercise.getId(), first)).isTrue();
        String second = ownershipService.claim(exercise.getId(), "edit");
        assertThat(ownershipService.release(exercise.getId(), first)).isFalse();
        assertThat(ownershipService.isOwned(exercise.getId(), second)).isTrue();
        assertThat(ownershipService.release(exercise.getId(), second)).isTrue();
        assertThat(ownershipService.get(exercise.getId())).isEmpty();
    }

    @Test
    void staleEntitySaveDoesNotEraseNativeAcquiredOwnership() {
        ProgrammingExercise stale = programmingExerciseRepository.findById(exercise.getId()).orElseThrow();
        String token = ownershipService.claim(exercise.getId(), "persist");
        stale.setTitle("An instructor edit on a stale entity");
        programmingExerciseRepository.saveAndFlush(stale);
        assertThat(ownershipService.isOwned(exercise.getId(), token)).isTrue();
        assertThat(ownershipService.get(exercise.getId())).hasValueSatisfying(owner -> assertThat(owner.purpose()).isEqualTo("persist"));
        ownershipService.release(exercise.getId(), token);
    }

    @Test
    void retainedRecoveryTransfersOnlyTheExactTokenAndPurpose() {
        String first = ownershipService.claim(exercise.getId(), "undo");
        assertThat(ownershipService.retain(exercise.getId(), "wrong", "recovery")).isFalse();
        assertThat(ownershipService.retain(exercise.getId(), first, "recovery")).isTrue();
        String retry = UUID.randomUUID().toString();
        assertThat(ownershipService.replaceRecovery(exercise.getId(), first, "undo", retry, "undo")).isFalse();
        assertThat(ownershipService.replaceRecovery(exercise.getId(), first, "recovery", retry, "undo")).isTrue();
        assertThat(ownershipService.release(exercise.getId(), first)).isFalse();
        assertThat(ownershipService.isOwned(exercise.getId(), retry)).isTrue();
        ownershipService.release(exercise.getId(), retry);
    }

    @Test
    void concurrentIndependentTransactionsHaveExactlyOneWinner() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return ownershipRepository.claim(exercise.getId(), "one", "process-one", "persist", Instant.now());
            });
            var two = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return ownershipRepository.claim(exercise.getId(), "two", "process-two", "persist", Instant.now());
            });
            start.countDown();
            assertThat(one.get(10, TimeUnit.SECONDS) + two.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        var owner = ownershipService.get(exercise.getId()).orElseThrow();
        assertThat(owner.token()).isIn("one", "two");
        ownershipService.release(exercise.getId(), owner.token());
    }

    @Test
    void competingClaimWaitsForTheActualDatabaseRowLock() throws Exception {
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> ownershipRepository.claimAndHold(exercise.getId(), "held", acquired, release));
            try {
                assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> ownershipRepository.claim(exercise.getId(), "waiting", "other-process", "persist", Instant.now()));
                await().atMost(Duration.ofSeconds(10)).until(this::ownershipUpdateIsWaitingOnRowLock);
                assertThat(second.isDone()).isFalse();
                release.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
                assertThat(second.get(10, TimeUnit.SECONDS)).isZero();
                assertThat(ownershipService.isOwned(exercise.getId(), "held")).isTrue();
            }
            finally {
                release.countDown();
            }
        }
        ownershipService.release(exercise.getId(), "held");
    }

    private boolean ownershipUpdateIsWaitingOnRowLock() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            boolean postgres = connection.getMetaData().getDatabaseProductName().equals("PostgreSQL");
            String sql = postgres ? "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock' AND query LIKE '%write_owner_token%'"
                    : "SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state = 'LOCK WAIT' AND trx_query LIKE '%write_owner_token%'";
            try (var rows = statement.executeQuery(sql)) {
                rows.next();
                return rows.getInt(1) > 0;
            }
        }
    }

    @Test
    void differentExercisesCanBeClaimedIndependently() {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var other = course.getExercises().iterator().next();
        String first = ownershipService.claim(exercise.getId(), "edit");
        String second = ownershipService.claim(other.getId(), "edit");
        assertThat(ownershipService.isOwned(exercise.getId(), first)).isTrue();
        assertThat(ownershipService.isOwned(other.getId(), second)).isTrue();
        ownershipService.release(exercise.getId(), first);
        ownershipService.release(other.getId(), second);
    }

    @Test
    void testBuildAuthorizationBelongsOnlyToTheExactOwnerAndIsClearedForRecovery() {
        String token = ownershipService.claim(exercise.getId(), "SAVE");
        assertThat(ownershipService.authorizeTestsBuild(exercise.getId(), "wrong", "a".repeat(40))).isFalse();
        assertThat(ownershipService.authorizeTestsBuild(exercise.getId(), token, "b".repeat(40))).isTrue();
        assertThat(ownershipService.get(exercise.getId())).hasValueSatisfying(owner -> assertThat(owner.expectedTestsCommitHash()).isEqualTo("b".repeat(40)));
        assertThat(ownershipService.retain(exercise.getId(), token, "SAVE_RECOVERY")).isTrue();
        assertThat(ownershipService.get(exercise.getId())).hasValueSatisfying(owner -> assertThat(owner.expectedTestsCommitHash()).isNull());
        assertThat(ownershipService.release(exercise.getId(), token)).isTrue();
        String next = ownershipService.claim(exercise.getId(), "SAVE");
        assertThat(ownershipService.get(exercise.getId())).hasValueSatisfying(owner -> assertThat(owner.expectedTestsCommitHash()).isNull());
        ownershipService.release(exercise.getId(), next);
    }

    @Test
    void missingExerciseCannotAcquireOwnership() {
        assertThat(ownershipRepository.claim(Long.MAX_VALUE, "missing", "process", "persist", Instant.now())).isZero();
        assertThat(ownershipService.get(Long.MAX_VALUE)).isEmpty();
    }
}
