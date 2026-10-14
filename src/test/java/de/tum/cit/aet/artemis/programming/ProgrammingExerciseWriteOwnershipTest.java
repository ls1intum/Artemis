package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.assessment.service.ExampleSubmissionService;
import de.tum.cit.aet.artemis.assessment.test_repository.TutorParticipationTestRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseWriteOwnershipService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseWriteOwnershipTestRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

class ProgrammingExerciseWriteOwnershipTest extends AbstractProgrammingIntegrationIndependentTest {

    @Autowired
    private ProgrammingExerciseWriteOwnershipTestRepository ownershipRepository;

    @Autowired
    private ProgrammingExerciseWriteOwnershipService ownershipService;

    @Autowired
    private ProgrammingExerciseMutationGuardService springMutationGuard;

    @Autowired
    private DataSource dataSource;

    private ProgrammingExercise exercise;

    private String mysqlObserverUrl;

    private String mysqlObserverPassword;

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
    void springInjectedMutationGuardResolvesDurableOwnershipOnWrite() {
        long id = exercise.getId();
        assertThat(springMutationGuard.isCoordinationEnabled()).isTrue();
        try (var lease = springMutationGuard.claimExternalMutation(id)) {
            assertThat(lease.coordinated()).isTrue();
            assertThat(ownershipService.get(id)).hasValueSatisfying(owner -> assertThat(owner.purpose()).isEqualTo("WRITE"));
        }
        assertThat(ownershipService.get(id)).isEmpty();
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

    @ParameterizedTest
    @ValueSource(strings = { "SAVE", "COPY" })
    void durableOwnershipBlocksTheSharedDeletionEntryBeforeAnyDestructiveCall(String purpose) {
        long id = exercise.getId();
        String token = purpose.equals("COPY") ? ownershipService.claimReader(id) : ownershipService.claim(id, purpose);
        var deletionRepository = mock(ExerciseTestRepository.class);
        when(deletionRepository.findWithCompetenciesByIdElseThrow(id)).thenReturn(exercise);
        var channels = mock(ChannelService.class);
        var participations = mock(ParticipationDeletionService.class);
        var programmingDeletion = mock(ProgrammingExerciseDeletionService.class);
        var guard = new ProgrammingExerciseMutationGuardService(Optional.of(new HyperionExerciseMutationApi(new GenerationExternalMutationService(ownershipService))));
        var deletion = new ExerciseDeletionService(deletionRepository, participations, programmingDeletion, mock(QuizExerciseService.class),
                mock(TutorParticipationTestRepository.class), mock(ExampleSubmissionService.class), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                channels, Optional.empty(), Optional.empty(), Optional.empty(), guard);
        try {
            assertThatThrownBy(() -> deletion.delete(id, true)).isInstanceOf(ConflictException.class);
            verifyNoInteractions(channels, participations, programmingDeletion);
            assertThat(ownershipRepository.existsById(id)).isTrue();
        }
        finally {
            if (purpose.equals("COPY")) {
                ownershipService.releaseReader(id, token);
            }
            else {
                ownershipService.release(id, token);
            }
        }
    }

    @Test
    void delayedSaveCleanupCannotReleaseSameTokenRecovery() {
        long id = exercise.getId();
        String token = ownershipService.claim(id, "SAVE");
        assertThat(ownershipService.releaseActiveSave(id, "wrong-token")).isFalse();
        assertThat(ownershipService.retain(id, token, "SAVE_RECOVERY")).isTrue();
        assertThat(ownershipService.releaseActiveSave(id, token)).isFalse();
        assertThat(ownershipService.get(id).orElseThrow().purpose()).isEqualTo("SAVE_RECOVERY");
        assertThat(ownershipService.release(id, token)).isTrue();
        String active = ownershipService.claim(id, "SAVE");
        assertThat(ownershipService.releaseActiveSave(id, active)).isTrue();
        assertThat(ownershipService.get(id)).isEmpty();
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
                prepareTestLockObserver();
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

    private void prepareTestLockObserver() throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
                return;
            }
            String url = connection.getMetaData().getURL();
            int port = URI.create(url.substring("jdbc:".length())).getPort();
            var docker = DockerClientFactory.instance().client();
            var container = docker.listContainersCmd().exec().stream()
                    .filter(candidate -> Arrays.stream(candidate.getPorts()).anyMatch(binding -> Integer.valueOf(port).equals(binding.getPublicPort()))).findFirst().orElseThrow();
            String password = Arrays.stream(docker.inspectContainerCmd(container.getId()).exec().getConfig().getEnv()).filter(value -> value.startsWith("MYSQL_ROOT_PASSWORD="))
                    .map(value -> value.substring("MYSQL_ROOT_PASSWORD=".length())).findFirst().orElseThrow();
            // Observe only the owned test database as its administrator. Existing pooled
            // application sessions need no PROCESS grant or privilege refresh.
            mysqlObserverUrl = url;
            mysqlObserverPassword = password;
        }
    }

    private boolean ownershipUpdateIsWaitingOnRowLock() throws Exception {
        try (var connection = mysqlObserverUrl == null ? dataSource.getConnection() : DriverManager.getConnection(mysqlObserverUrl, "root", mysqlObserverPassword);
                var statement = connection.createStatement()) {
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

    @Test
    void parallelCopiesExcludeWritesUntilTheLastExactReaderCloses() throws Exception {
        long id = exercise.getId();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> {
                start.await();
                return ownershipService.claimReader(id);
            });
            var two = executor.submit(() -> {
                start.await();
                return ownershipService.claimReader(id);
            });
            start.countDown();
            String first = one.get(10, TimeUnit.SECONDS);
            String second = two.get(10, TimeUnit.SECONDS);
            assertThat(ownershipService.readers(id)).hasSize(2);
            assertThatThrownBy(() -> ownershipService.claim(id, "SAVE")).isInstanceOf(ConflictException.class);
            assertThat(ownershipService.releaseReader(id, first)).isTrue();
            assertThat(ownershipService.releaseReader(id, first)).isFalse();
            assertThatThrownBy(() -> ownershipService.claim(id, "SAVE")).isInstanceOf(ConflictException.class);
            assertThat(ownershipService.releaseReader(id, second)).isTrue();
            String writer = ownershipService.claim(id, "SAVE");
            assertThatThrownBy(() -> ownershipService.claimReader(id)).isInstanceOf(ConflictException.class);
            assertThat(ownershipService.release(id, writer)).isTrue();
        }
    }

    @Test
    void aFailedReaderInsertRollsBackItsCounter() {
        long id = exercise.getId();
        String token = UUID.randomUUID().toString();
        assertThat(ownershipRepository.claimReader(id, token, "owner", Instant.now())).isOne();
        assertThatThrownBy(() -> ownershipRepository.claimReader(id, token, "owner", Instant.now())).isInstanceOf(RuntimeException.class);
        assertThat(ownershipRepository.releaseReader(id, token)).isOne();
        String writer = ownershipService.claim(id, "SAVE");
        assertThat(ownershipService.release(id, writer)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void readerAndWriterContendOnTheSameActualRowLock(boolean readerFirst) throws Exception {
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        prepareTestLockObserver();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> readerFirst ? ownershipRepository.claimReaderAndHold(exercise.getId(), "held-reader", acquired, release)
                    : ownershipRepository.claimAndHold(exercise.getId(), "held-writer", acquired, release));
            try {
                assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> readerFirst ? ownershipRepository.claim(exercise.getId(), "waiting-writer", "other-process", "SAVE", Instant.now())
                        : ownershipRepository.claimReader(exercise.getId(), "waiting-reader", "other-process", Instant.now()));
                await().atMost(Duration.ofSeconds(10)).until(this::ownershipUpdateIsWaitingOnRowLock);
                assertThat(second.isDone()).isFalse();
                release.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS)).isOne();
                assertThat(second.get(10, TimeUnit.SECONDS)).isZero();
                if (readerFirst) {
                    assertThat(ownershipService.readers(exercise.getId())).hasSize(1);
                    assertThat(ownershipService.get(exercise.getId())).isEmpty();
                    assertThat(ownershipService.releaseReader(exercise.getId(), "held-reader")).isTrue();
                }
                else {
                    assertThat(ownershipService.readers(exercise.getId())).isEmpty();
                    assertThat(ownershipService.isOwned(exercise.getId(), "held-writer")).isTrue();
                    assertThat(ownershipService.release(exercise.getId(), "held-writer")).isTrue();
                }
            }
            finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void tokenCaseAndWhitespaceVariantsCannotReleaseTheActualReservation(boolean reader) {
        long id = exercise.getId();
        String token = reader ? ownershipService.claimReader(id) : ownershipService.claim(id, "SAVE");
        for (String variant : new String[] { token.toUpperCase(Locale.ROOT), token + " " }) {
            assertThatThrownBy(() -> {
                if (reader) {
                    ownershipService.releaseReader(id, variant);
                }
                else {
                    ownershipService.release(id, variant);
                }
            }).isInstanceOf(IllegalArgumentException.class);
        }
        if (reader) {
            assertThat(ownershipService.readers(id)).hasSize(1).allSatisfy(owner -> assertThat(owner.token()).isEqualTo(token));
            assertThat(ownershipService.releaseReader(id, token)).isTrue();
        }
        else {
            assertThat(ownershipService.isOwned(id, token)).isTrue();
            assertThat(ownershipService.release(id, token)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "close", "retain", "release" })
    void admittedCallbackFinishesItsSqlBeforeClosingOrChangingOwnership(String operation) throws Exception {
        long id = exercise.getId();
        String token = ownershipService.claim(id, "SAVE");
        String hash = "a".repeat(40);
        assertThat(ownershipService.authorizeTestsBuild(id, token, hash)).isTrue();
        var admitted = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        prepareTestLockObserver();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var callback = executor.submit(() -> ownershipService.executeAuthorizedTestsBuild(id, token, hash, () -> {
                admitted.countDown();
                try {
                    if (!finish.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test did not finish the admitted callback");
                    }
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return ownershipRepository.updateCallbackTitle(id, "Callback SQL finished") == 1;
            }));
            try {
                assertThat(admitted.await(10, TimeUnit.SECONDS)).isTrue();
                var close = executor.submit(() -> switch (operation) {
                    case "close" -> ownershipService.stopTestsBuildAuthorization(id, token);
                    case "retain" -> ownershipService.retain(id, token, "SAVE_RECOVERY");
                    case "release" -> ownershipService.release(id, token);
                    default -> throw new IllegalArgumentException(operation);
                });
                await().atMost(Duration.ofSeconds(10)).until(this::ownershipUpdateIsWaitingOnRowLock);
                assertThat(close.isDone()).isFalse();
                finish.countDown();
                assertThat(callback.get(10, TimeUnit.SECONDS)).contains(true);
                assertThat(close.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(programmingExerciseRepository.findById(id).orElseThrow().getTitle()).isEqualTo("Callback SQL finished");
                var lateCalls = new java.util.concurrent.atomic.AtomicInteger();
                assertThat(ownershipService.executeAuthorizedTestsBuild(id, token, hash, () -> {
                    lateCalls.incrementAndGet();
                    return true;
                })).isEmpty();
                assertThat(lateCalls).hasValue(0);
            }
            finally {
                finish.countDown();
            }
        }
        if (!operation.equals("release")) {
            assertThat(ownershipService.isOwned(id, token)).isTrue();
            assertThat(ownershipService.release(id, token)).isTrue();
        }
    }
}
