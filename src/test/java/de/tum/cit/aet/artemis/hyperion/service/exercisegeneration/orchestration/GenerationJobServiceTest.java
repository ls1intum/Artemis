package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerationJobServiceTest {

    private HazelcastInstance hazelcastInstance;

    private GenerationJobService jobService;

    @BeforeAll
    void startHazelcast() {
        Config config = new Config();
        config.setClusterName("hyperion-job-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @BeforeEach
    void setUp() {
        hazelcastInstance.getDistributedObjects().forEach(distributedObject -> distributedObject.destroy());
        jobService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class));
        jobService.init();
    }

    @AfterAll
    void stopHazelcast() {
        hazelcastInstance.shutdown();
    }

    private static User user(String login) {
        User user = new User();
        user.setLogin(login);
        return user;
    }

    private static ProgrammingExercise exercise(long id) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }

    private static ExerciseGenerationEventDTO progress(String message) {
        return ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, message);
    }

    @Test
    void startJob_secondConcurrentStartForSameExercise_throwsConflict() {
        ProgrammingExercise exercise = exercise(42L);
        User owner = user("owner");

        assertThat(jobService.startJob(owner, exercise, "do it", GenerationMode.GENERATE)).isNotBlank();
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.startJob(owner, exercise, "again", GenerationMode.GENERATE));
    }

    @Test
    void startJob_simultaneousStartsForSameExercise_admitsExactlyOne() throws Exception {
        ProgrammingExercise exercise = exercise(420L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startTogether = new CyclicBarrier(2);
        try {
            Callable<Boolean> attempt = () -> {
                startTogether.await();
                try {
                    jobService.startJob(user("owner"), exercise, "go", GenerationMode.GENERATE);
                    return true;
                }
                catch (ConflictException ignored) {
                    return false;
                }
            };

            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void claimRevertSlot_blocksGenerationUntilCleared() {
        ProgrammingExercise exercise = exercise(43L);
        User owner = user("owner");

        String token = jobService.claimRevertSlot(owner, exercise.getId());

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.startJob(owner, exercise, "again", GenerationMode.GENERATE));
        jobService.clearRevertSlot(exercise.getId(), token);
        assertThat(jobService.startJob(owner, exercise, "after revert", GenerationMode.GENERATE)).isNotBlank();
    }

    @Test
    void claimRevertSlot_whenGenerationRuns_throwsConflict() {
        ProgrammingExercise exercise = exercise(44L);
        User owner = user("owner");
        jobService.startJob(owner, exercise, "do it", GenerationMode.GENERATE);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.claimRevertSlot(owner, exercise.getId()));
    }

    @Test
    void discardRetainedRun_removesOnlyTheMatchingTranscriptAndFileChanges() {
        long exerciseId = 45L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "adapt", GenerationMode.ADAPT);
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/A.java", "adapted"));
        jobService.clearJob(exerciseId, jobId);

        jobService.discardRetainedRun(exerciseId, "different-job");
        assertThat(jobService.getStatus(owner, exercise)).isPresent();

        jobService.discardRetainedRun(exerciseId, jobId);

        assertThat(jobService.getStatus(owner, exercise)).isEmpty();
        assertThat(fileChangeEntries()).isEmpty();
        assertThat(fileChangeMap().isEmpty()).isTrue();
    }

    @Test
    void discardRetainedRun_waitsForTheJobFileChangeMutex() throws Exception {
        long exerciseId = 46L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "adapt", GenerationMode.ADAPT);
        jobService.clearJob(exerciseId, jobId);
        String key = String.valueOf(exerciseId);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch discardStarted = new CountDownLatch(1);
        Future<?> discard;

        jobMap().lock(key);
        try {
            discard = executor.submit(() -> {
                discardStarted.countDown();
                jobService.discardRetainedRun(exerciseId, jobId);
            });
            assertThat(discardStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> discard.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(transcriptMap().get(key)).isNotNull();
        }
        finally {
            jobMap().unlock(key);
        }
        discard.get(2, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(jobService.getStatus(owner, exercise)).isEmpty();
        assertThat(fileChangeMap().isEmpty()).isTrue();
    }

    @Test
    void recordEvent_beyondCap_keepsStartedHeadAndDropsIndexOne_preservingOrder() {
        ProgrammingExercise exercise = exercise(7L);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);

        jobService.recordEvent(7L, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "STARTED-HEAD"), false);
        int overflow = 600;
        for (int i = 0; i < overflow; i++) {
            jobService.recordEvent(7L, jobId, progress("p" + i), false);
        }

        List<ExerciseGenerationEventDTO> events = jobService.getStatus(owner, exercise).orElseThrow().events();

        assertThat(events).hasSize(500);
        assertThat(events.getFirst().type()).isEqualTo(ExerciseGenerationEventDTO.Type.STARTED);
        assertThat(events.getFirst().message()).isEqualTo("STARTED-HEAD");
        assertThat(events.get(1).message()).isEqualTo("p" + (overflow - 499));
        assertThat(events.getLast().message()).isEqualTo("p" + (overflow - 1));
    }

    @Test
    void getStatus_carriesExplicitMode_soReconnectRestoresAdaptAffordances() {
        ProgrammingExercise exercise = exercise(31L);
        User owner = user("owner");
        jobService.startJob(owner, exercise, "fix it", GenerationMode.ADAPT);

        assertThat(jobService.getStatus(owner, exercise).orElseThrow().mode()).isEqualTo(GenerationMode.ADAPT);
    }

    @Test
    void getStatus_waitsForAnInFlightJobMutationBeforeReadingTheStatusFileChange() throws Exception {
        ProgrammingExercise exercise = exercise(310L);
        User owner = user("owner");
        jobService.startJob(owner, exercise, "fix it", GenerationMode.ADAPT);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        String key = String.valueOf(exercise.getId());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch readerStarted = new CountDownLatch(1);
        AtomicBoolean lockHeld = new AtomicBoolean(true);

        jobMap.lock(key);
        try {
            Future<Optional<ExerciseGenerationStatusDTO>> status = executor.submit(() -> {
                readerStarted.countDown();
                return jobService.getStatus(owner, exercise);
            });
            assertThat(readerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> status.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            jobMap.unlock(key);
            lockHeld.set(false);
            assertThat(status.get(5, TimeUnit.SECONDS)).isPresent();
        }
        finally {
            if (lockHeld.get()) {
                jobMap.unlock(key);
            }
            executor.shutdownNow();
        }
    }

    @Test
    void recordEvent_refreshesTheActiveJobSlotTtl_soLongRunningJobsKeepSingleFlight() throws Exception {
        ProgrammingExercise exercise = exercise(32L);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        String key = String.valueOf(exercise.getId());
        jobMap.set(key, jobMap.get(key), 1, TimeUnit.SECONDS);

        jobService.recordEvent(32L, jobId, progress("heartbeat"), false);

        await().pollDelay(Duration.ofMillis(1300)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.startJob(owner, exercise, "again", GenerationMode.GENERATE)));
    }

    @Test
    void recordFileChange_refreshesTheActiveJobSlotTtl_soFileChangeHeavyRunsKeepSingleFlight() throws Exception {
        ProgrammingExercise exercise = exercise(33L);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        String key = String.valueOf(exercise.getId());
        jobMap.set(key, jobMap.get(key), 1, TimeUnit.SECONDS);

        jobService.recordFileChange(33L, jobId, fileChange("solution/Heartbeat.java", "changed"));

        await().pollDelay(Duration.ofMillis(1300)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.startJob(owner, exercise, "again", GenerationMode.GENERATE)));
    }

    @Test
    void activityRefreshesRetainedFileChangesDuringLongRuns() {
        long exerciseId = 35L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        String key = String.valueOf(exerciseId);
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/Preview.java", "initial"));
        fileChangeMap().set(key, fileChangeMap().get(key), 1, TimeUnit.SECONDS);

        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);

        await().pollDelay(Duration.ofMillis(1300)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(jobService.getStatus(owner, exercise).orElseThrow().fileChanges())
                .singleElement().extracting(ExerciseGenerationFileChangeDTO::path).isEqualTo("solution/Preview.java"));
    }

    @Test
    void getStatus_forDifferentUser_returnsSanitizedActiveRunWithoutPrivateDetails() {
        ProgrammingExercise exercise = exercise(99L);
        jobService.startJob(user("instructorA"), exercise, "go", GenerationMode.GENERATE);

        assertThat(jobService.getStatus(user("instructorA"), exercise)).hasValueSatisfying(status -> assertThat(status.ownedByCaller()).isTrue());
        assertThat(jobService.getStatus(user("instructorB"), exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isTrue();
            assertThat(status.ownedByCaller()).isFalse();
            assertThat(status.events()).isEmpty();
            assertThat(status.fileChanges()).isEmpty();
            assertThat(status.cancellable()).isFalse();
        });
    }

    @Test
    void getStatus_forDifferentUser_returnsOnlyTheTerminalMutationOutcome() {
        long exerciseId = 991L;
        ProgrammingExercise exercise = exercise(exerciseId);
        String jobId = jobService.startJob(user("instructorA"), exercise, "private prompt", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.done("private result", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS,
                new ExerciseGenerationVerdictDTO(true, true, true, 3, List.of()), true), true);
        jobService.clearJob(exerciseId, jobId);

        assertThat(jobService.getStatus(user("instructorB"), exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isFalse();
            assertThat(status.ownedByCaller()).isFalse();
            assertThat(status.fileChanges()).isEmpty();
            assertThat(status.events()).singleElement().satisfies(event -> {
                assertThat(event.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
                assertThat(event.message()).isNull();
                assertThat(event.verdict()).isNull();
                assertThat(event.liveExerciseChanged()).isTrue();
            });
        });
    }

    @Test
    void getStatus_usesActiveSlotOwnershipWhenRetainedTranscriptBelongsToPreviousRun() {
        long exerciseId = 100L;
        ProgrammingExercise exercise = exercise(exerciseId);
        jobService.startJob(user("previousOwner"), exercise, "first", GenerationMode.GENERATE);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        Instant now = Instant.now();
        jobMap.set(String.valueOf(exerciseId), new GenerationJobService.JobInfo("new-job", "currentowner", exerciseId, now, now.plusSeconds(60), "node", now, false, null));

        assertThat(jobService.getStatus(user("previousOwner"), exercise)).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo("new-job");
            assertThat(status.running()).isTrue();
            assertThat(status.ownedByCaller()).isFalse();
        });
        assertThat(jobService.getStatus(user("currentOwner"), exercise)).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo("new-job");
            assertThat(status.running()).isTrue();
            assertThat(status.ownedByCaller()).isTrue();
            assertThat(status.cancellable()).isFalse();
        });
    }

    @Test
    void getStatus_hidesCancellationAfterDurablePhaseStarts() {
        ProgrammingExercise exercise = exercise(101L);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);

        assertThat(jobService.getStatus(owner, exercise).orElseThrow().cancellable()).isTrue();
        assertThat(jobService.enterNonCancellablePhase(exercise.getId(), jobId)).isTrue();
        assertThat(jobService.getStatus(owner, exercise).orElseThrow().cancellable()).isFalse();
    }

    @Test
    void requestCancellation_runsCancelHookExactlyOnce_thenRemovesIt() {
        String jobId = jobService.startJob(user("owner"), exercise(11L), "go", GenerationMode.GENERATE);
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);

        assertThat(jobService.requestCancellation(11L, jobId, user("owner"))).isTrue();
        assertThat(hookRuns.get()).isEqualTo(1);
        assertThat(jobService.isCancelled(jobId)).isTrue();

        assertThat(jobService.requestCancellation(11L, jobId, user("owner"))).isFalse();
        assertThat(hookRuns.get()).isEqualTo(1);
    }

    @Test
    void requestCancellation_terminalizesTheUiButRetainsTheSlotUntilTheWorkerDrains() {
        long exerciseId = 112L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, progress("working"), false);
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/Before.java", "before"));

        assertThat(jobService.requestCancellation(exerciseId, jobId, owner)).isTrue();

        ExerciseGenerationStatusDTO cancelled = jobService.getStatus(owner, exercise).orElseThrow();
        assertThat(cancelled.running()).isFalse();
        assertThat(cancelled.cancellable()).isFalse();
        assertThat(cancelled.events()).extracting(ExerciseGenerationEventDTO::type).containsExactly(ExerciseGenerationEventDTO.Type.PROGRESS,
                ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(cancelled.events().getLast().message()).isEqualTo("Generation was cancelled. Nothing was changed.");
        assertThat(cancelled.fileChanges()).extracting(ExerciseGenerationFileChangeDTO::path).containsExactly("solution/Before.java");
        assertThat(jobService.hasActiveJob(exerciseId)).isTrue();
        assertThat(jobService.isCancelled(jobId)).isTrue();
        assertThat(jobService.requestCancellation(exerciseId, jobId, owner)).isFalse();

        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.done("late success", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true), true);
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/Late.java", "late"));

        ExerciseGenerationStatusDTO afterLateCompletion = jobService.getStatus(owner, exercise).orElseThrow();
        assertThat(afterLateCompletion.events()).isEqualTo(cancelled.events());
        assertThat(afterLateCompletion.fileChanges()).isEqualTo(cancelled.fileChanges());

        assertThatThrownBy(() -> jobService.startJob(owner, exercise, "retry", GenerationMode.GENERATE)).isInstanceOf(ConflictException.class);
        jobService.clearJob(exerciseId, jobId);
        String successorJobId = jobService.startJob(owner, exercise, "retry", GenerationMode.GENERATE);
        assertThat(jobService.isActiveJob(exerciseId, successorJobId)).isTrue();
    }

    @Test
    void initRejectsFailOpenDeadlineAndStaleTimeoutConfiguration() {
        GenerationJobService invalid = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertThatThrownBy(invalid::init).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stale-job-timeout");
    }

    @Test
    void requestCancellation_publishesCancelToOtherServiceInstances() {
        GenerationJobService ownerNode = jobService;
        GenerationJobService apiNode = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class));
        apiNode.init();
        String jobId = ownerNode.startJob(user("owner"), exercise(111L), "go", GenerationMode.GENERATE);
        AtomicInteger ownerHookRuns = new AtomicInteger(0);
        ownerNode.registerCancelHook(jobId, ownerHookRuns::incrementAndGet);

        assertThat(apiNode.requestCancellation(111L, jobId, user("owner"))).isTrue();

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(ownerHookRuns).hasValue(1));
        assertThat(apiNode.isCancelled(jobId)).isTrue();
        assertThat(ownerNode.hasActiveJob(111L)).isTrue();
        ExerciseGenerationStatusDTO status = ownerNode.getStatus(user("owner"), exercise(111L)).orElseThrow();
        assertThat(status.running()).isFalse();
        assertThat(status.events()).extracting(ExerciseGenerationEventDTO::type).containsExactly(ExerciseGenerationEventDTO.Type.CANCELLED);
    }

    @Test
    void requestCancellation_byANonOwner_isRefused_andDoesNotCancel() {
        String jobId = jobService.startJob(user("owner"), exercise(12L), "go", GenerationMode.GENERATE);
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);

        assertThat(jobService.requestCancellation(12L, jobId, user("notOwner"))).isFalse();
        assertThat(hookRuns.get()).isZero();
        assertThat(jobService.isCancelled(jobId)).isFalse();
        assertThat(jobService.requestCancellation(12L, jobId, user("owner"))).isTrue();
        assertThat(jobService.isCancelled(jobId)).isTrue();
    }

    @Test
    void enterNonCancellablePhase_whenNoCancellationWasRequested_refusesLaterCancellation() {
        long exerciseId = 13L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);

        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();

        assertThat(jobService.requestCancellation(exerciseId, jobId, user("owner"))).isFalse();
        assertThat(jobService.isCancelled(jobId)).isFalse();
        assertThat(hookRuns).hasValue(0);
    }

    @Test
    void enterNonCancellablePhase_refusesLaterSystemCancellation() {
        long exerciseId = 130L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);

        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();

        assertThat(jobService.requestSystemCancellation(exerciseId, jobId)).isFalse();
        assertThat(jobService.isCancelled(jobId)).isFalse();
        assertThat(hookRuns).hasValue(0);
    }

    @Test
    void enterNonCancellablePhase_whenCancellationAlreadyRequested_refusesToEnterAndLeavesTheRunCancelled() {
        long exerciseId = 14L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);

        assertThat(jobService.requestCancellation(exerciseId, jobId, user("owner"))).isTrue();

        // Cancellation already won this job under the job-map lock: the caller must not be allowed to persist, and the run stays cancelled — never both.
        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isFalse();
        assertThat(jobService.isCancelled(jobId)).isTrue();
        ExerciseGenerationStatusDTO status = jobService.getStatus(user("owner"), exercise(exerciseId)).orElseThrow();
        assertThat(status.events()).extracting(ExerciseGenerationEventDTO::type).containsExactly(ExerciseGenerationEventDTO.Type.CANCELLED);
    }

    @Test
    void requestCancellation_afterEnteringNonCancellablePhase_refusesAndDoesNotOverwriteTheSaveObligation() {
        long exerciseId = 141L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);

        // Persistence already won this job under the job-map lock: a later cancel request must not succeed or retroactively claim "nothing was changed".
        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();

        assertThat(jobService.requestCancellation(exerciseId, jobId, user("owner"))).isFalse();
        assertThat(jobService.isCancelled(jobId)).isFalse();
        ExerciseGenerationStatusDTO status = jobService.getStatus(user("owner"), exercise(exerciseId)).orElseThrow();
        assertThat(status.events()).isEmpty();
        assertThat(status.cancellable()).isFalse();
    }

    @Test
    void cancellationAndNonCancellableCutoff_areMutuallyExclusiveUnderTheDistributedJobLock() throws Exception {
        for (int i = 0; i < 30; i++) {
            long exerciseId = 200L + i;
            String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CyclicBarrier startTogether = new CyclicBarrier(2);
            try {
                Future<Boolean> cutoff = executor.submit(() -> {
                    startTogether.await();
                    return jobService.enterNonCancellablePhase(exerciseId, jobId);
                });
                Future<Boolean> cancellation = executor.submit(() -> {
                    startTogether.await();
                    return jobService.requestCancellation(exerciseId, jobId, user("owner"));
                });

                boolean cutoffAccepted = cutoff.get(5, TimeUnit.SECONDS);
                boolean cancellationAccepted = cancellation.get(5, TimeUnit.SECONDS);

                assertThat(cutoffAccepted).isNotEqualTo(cancellationAccepted);
                assertThat(jobService.isCancelled(jobId)).isEqualTo(cancellationAccepted);
            }
            finally {
                executor.shutdownNow();
                jobService.clearJob(exerciseId, jobId);
            }
        }
    }

    @Test
    void getStatus_emptyWhenNothingRetained() {
        assertThat(jobService.getStatus(user("owner"), exercise(123L))).isEmpty();
    }

    @Test
    void heartbeat_refreshesOwnerLivenessWithoutProgressEvents() {
        long exerciseId = 124L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        Instant before = jobMap.get(String.valueOf(exerciseId)).lastHeartbeatOrStartedAt();

        assertThat(jobService.heartbeat(exerciseId, jobId)).isTrue();

        assertThat(jobMap.get(String.valueOf(exerciseId)).lastHeartbeatOrStartedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void heartbeat_refreshesTheInFlightBudgetReservation() {
        long exerciseId = 224L;
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        GenerationJobService service = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(35), Duration.ofMinutes(30));
        service.init();
        String jobId = service.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE, "reservation-224");

        assertThat(service.heartbeat(exerciseId, jobId)).isTrue();

        verify(budgetService).refreshReservation("reservation-224");
    }

    @Test
    void clearStaleJobs_marksTranscriptDoneAndReleasesSlot() {
        long exerciseId = 125L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        jobMap.set(String.valueOf(exerciseId), new GenerationJobService.JobInfo(jobId, "owner", exerciseId, Instant.now().minus(Duration.ofMinutes(10)), null, "departed-node",
                Instant.now().minus(Duration.ofMinutes(10)), true, null));
        GenerationJobService shortTimeoutService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofSeconds(30));
        shortTimeoutService.init();

        shortTimeoutService.clearStaleJobs();

        ExerciseGenerationStatusDTO status = shortTimeoutService.getStatus(owner, exercise).orElseThrow();
        assertThat(status.running()).isFalse();
        assertThat(status.events().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(status.events().getLast().message()).contains("heartbeats");
        assertThat(shortTimeoutService.hasActiveJob(exerciseId)).isFalse();
    }

    @Test
    void staleHeartbeatWithLiveOwner_cancelsButRetainsTheSlotUntilTheWorkerDrains() {
        long exerciseId = 225L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE, "reservation-225");
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        GenerationJobService scannerNode = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(1), Duration.ofSeconds(30));
        scannerNode.init();

        scannerNode.clearStaleJobs();

        assertThat(scannerNode.isCancelled(jobId)).isTrue();
        assertThat(scannerNode.hasActiveJob(exerciseId)).isTrue();
        assertThat(scannerNode.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isFalse();
            assertThat(status.events().getLast().message()).contains("heartbeats");
        });
        verify(budgetService, never()).releaseReservation("reservation-225");
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> scannerNode.startJob(owner, exercise, "new", GenerationMode.GENERATE));
    }

    @Test
    void clearStaleJobs_broadcastsCancellationAndRejectsLateReplayWrites() {
        long exerciseId = 126L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/Before.java", "before"));
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        GenerationJobService scannerNode = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofSeconds(30));
        scannerNode.init();

        scannerNode.clearStaleJobs();
        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.done("late success", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true), true);
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/After.java", "after"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(hookRuns).hasValue(1));
        ExerciseGenerationStatusDTO status = jobService.getStatus(owner, exercise).orElseThrow();
        assertThat(status.running()).isFalse();
        assertThat(status.events().getLast().message()).contains("heartbeats");
        assertThat(status.fileChanges()).singleElement().satisfies(fileChange -> {
            assertThat(fileChange.path()).isEqualTo("solution/Before.java");
        });
    }

    @Test
    void clearStaleJobs_releasesBudgetReservationForAbandonedJob() {
        long exerciseId = 127L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE, "reservation-127");
        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        GenerationJobService scannerNode = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(1), Duration.ofSeconds(30));
        scannerNode.init();

        scannerNode.clearStaleJobs();

        verify(budgetService).releaseReservation("reservation-127");
        assertThat(scannerNode.hasActiveJob(exerciseId)).isFalse();
    }

    @Test
    void startJob_reclaimsStaleSlotInsteadOfRejectingUntilScheduledScanRuns() {
        long exerciseId = 127L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String staleJobId = jobService.startJob(owner, exercise, "old", GenerationMode.GENERATE);
        forceJobHeartbeat(exerciseId, staleJobId, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        GenerationJobService shortTimeoutService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofSeconds(30));
        shortTimeoutService.init();

        String freshJobId = shortTimeoutService.startJob(owner, exercise, "new", GenerationMode.GENERATE);

        assertThat(freshJobId).isNotBlank().isNotEqualTo(staleJobId);
        ExerciseGenerationStatusDTO status = shortTimeoutService.getStatus(owner, exercise).orElseThrow();
        assertThat(status.jobId()).isEqualTo(freshJobId);
        assertThat(status.running()).isTrue();
    }

    @Test
    void expiredDeadline_doesNotReclaimNonCancellablePersistenceSlot() {
        long exerciseId = 128L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        GenerationJobService shortDeadlineService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofHours(1), Duration.ofMillis(1));
        shortDeadlineService.init();
        String jobId = shortDeadlineService.startJob(owner, exercise, "old", GenerationMode.GENERATE);
        assertThat(shortDeadlineService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
        forceJobDeadline(shortDeadlineService, exerciseId, Instant.now().minusSeconds(1));

        shortDeadlineService.clearStaleJobs();

        assertThat(shortDeadlineService.getStatus(owner, exercise)).hasValueSatisfying(status -> assertThat(status.running()).isTrue());
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> shortDeadlineService.startJob(owner, exercise, "new", GenerationMode.GENERATE));
    }

    @Test
    void expiredDeadline_doesNotDestroyAJobWhoseOwnerStillSendsHeartbeats() {
        long exerciseId = 129L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "old", GenerationMode.GENERATE);
        forceJobDeadline(jobService, exerciseId, Instant.now().minusSeconds(1));

        jobService.clearStaleJobs();

        assertThat(jobService.getStatus(owner, exercise)).hasValueSatisfying(status -> assertThat(status.running()).isTrue());
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.startJob(owner, exercise, "new", GenerationMode.GENERATE));
        assertThat(jobService.isCancelled(jobId)).isFalse();
    }

    @Test
    void staleHeartbeat_reclaimsNonCancellablePersistenceSlotAfterOwnerLeavesCluster() {
        long exerciseId = 228L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "old", GenerationMode.GENERATE, "reservation-228");
        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        GenerationJobService shortTimeoutService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(1), Duration.ofSeconds(30));
        shortTimeoutService.init();

        shortTimeoutService.clearStaleJobs();

        assertThat(shortTimeoutService.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isFalse();
            assertThat(status.events().getLast()).satisfies(event -> {
                assertThat(event.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
                assertThat(event.liveExerciseChanged()).isTrue();
                assertThat(event.message()).contains("saving");
            });
        });
        assertThat(shortTimeoutService.hasActiveJob(exerciseId)).isFalse();
        verify(budgetService).releaseReservation("reservation-228");
        assertThat(shortTimeoutService.startJob(owner, exercise, "new", GenerationMode.GENERATE)).isNotBlank();
    }

    @Test
    void registerCancelHook_afterCancellationAlreadyRequested_runsHookOnce() {
        long exerciseId = 129L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        assertThat(jobService.requestSystemCancellation(exerciseId, jobId)).isTrue();
        AtomicInteger hookRuns = new AtomicInteger();

        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);
        jobService.requestSystemCancellation(exerciseId, jobId);

        assertThat(hookRuns).hasValue(1);
    }

    private void forceJobHeartbeat(long exerciseId, String jobId, Instant heartbeatAt) {
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        GenerationJobService.JobInfo existing = jobMap.get(String.valueOf(exerciseId));
        String localNodeId = (String) ReflectionTestUtils.getField(jobService, "localNodeId");
        jobMap.set(String.valueOf(exerciseId), new GenerationJobService.JobInfo(jobId, existing.userLogin(), exerciseId, existing.startedAt(), existing.deadlineAt(), localNodeId,
                heartbeatAt, existing.cancellable(), existing.budgetReservationId()));
    }

    private void forceJobOwner(long exerciseId, String ownerNodeId) {
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        GenerationJobService.JobInfo existing = jobMap.get(String.valueOf(exerciseId));
        jobMap.set(String.valueOf(exerciseId), new GenerationJobService.JobInfo(existing.jobId(), existing.userLogin(), exerciseId, existing.startedAt(), existing.deadlineAt(),
                ownerNodeId, existing.lastHeartbeatAt(), existing.cancellable(), existing.budgetReservationId()));
    }

    private static void forceJobDeadline(GenerationJobService service, long exerciseId, Instant deadlineAt) {
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(service, "jobMap");
        GenerationJobService.JobInfo existing = jobMap.get(String.valueOf(exerciseId));
        jobMap.set(String.valueOf(exerciseId), new GenerationJobService.JobInfo(existing.jobId(), existing.userLogin(), exerciseId, existing.startedAt(), deadlineAt,
                existing.ownerNodeId(), existing.lastHeartbeatAt(), existing.cancellable(), existing.budgetReservationId()));
    }

    private static ExerciseGenerationFileChangeDTO fileChange(String path, String content) {
        return ExerciseGenerationFileChangeDTO.of(path, ExerciseGenerationFileChangeDTO.ACTION_WRITE, 1);
    }

    private IMap<String, GenerationJobService.JobInfo> jobMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-jobs");
    }

    private IMap<String, GenerationJobService.JobTranscript> transcriptMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-transcripts");
    }

    private IMap<String, GenerationJobService.JobFileChangeIndex> fileChangeMap() {
        return hazelcastInstance.getMap(GenerationJobService.FILE_CHANGE_MAP_NAME);
    }

    private Map<String, GenerationJobService.JobFileChangeIndex> fileChangeEntries() {
        return fileChangeMap();
    }

    @Test
    void recordFileChangeKeepsLatestChangePerPathAndBoundsReplay() {
        long exerciseId = 501L;
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);

        for (int i = 0; i < GenerationJobService.MAX_RETAINED_FILE_CHANGES + 1; i++) {
            jobService.recordFileChange(exerciseId, jobId, fileChange("solution/File" + i + ".java", "body"));
        }
        jobService.recordFileChange(exerciseId, jobId, ExerciseGenerationFileChangeDTO.of("solution/File1.java", ExerciseGenerationFileChangeDTO.ACTION_EDIT, 9));

        List<ExerciseGenerationFileChangeDTO> replay = jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileChanges();
        assertThat(replay).hasSize(GenerationJobService.MAX_RETAINED_FILE_CHANGES);
        assertThat(replay.getFirst().path()).isEqualTo("solution/File1.java");
        assertThat(replay.getFirst().action()).isEqualTo(ExerciseGenerationFileChangeDTO.ACTION_EDIT);
        assertThat(replay.getFirst().turn()).isEqualTo(9);
        assertThat(replay.getLast().path()).isEqualTo("solution/File" + GenerationJobService.MAX_RETAINED_FILE_CHANGES + ".java");
    }

    @Test
    void recordFileChangeDropsStaleJobAndNewRunStartsClean() {
        long exerciseId = 502L;
        User owner = user("owner");
        String firstJob = jobService.startJob(owner, exercise(exerciseId), "first", GenerationMode.GENERATE);
        jobService.recordFileChange(exerciseId, firstJob, fileChange("solution/Old.java", "old"));
        jobService.clearJob(exerciseId, firstJob);

        String secondJob = jobService.startJob(owner, exercise(exerciseId), "second", GenerationMode.GENERATE);
        jobService.recordFileChange(exerciseId, firstJob, fileChange("solution/Stale.java", "stale"));

        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileChanges()).isEmpty();
        jobService.recordFileChange(exerciseId, secondJob, fileChange("solution/New.java", "new"));
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileChanges()).extracting(ExerciseGenerationFileChangeDTO::path)
                .containsExactly("solution/New.java");
    }

    @Test
    void startJob_whenExecutorRejectsPublish_releasesSlotAndReportsBusy_notWedged() {
        AtomicBoolean reject = new AtomicBoolean(true);
        AtomicInteger publishAttempts = new AtomicInteger(0);
        ApplicationEventPublisher rejectingPublisher = event -> {
            publishAttempts.incrementAndGet();
            if (reject.get()) {
                throw new TaskRejectedException("hyperionGenerationExecutor is saturated");
            }
        };
        GenerationJobService service = new GenerationJobService(hazelcastInstance, rejectingPublisher, mock(LLMTokenUsageService.class));
        service.init();

        ProgrammingExercise exercise = exercise(77L);
        User owner = user("owner");

        assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> service.startJob(owner, exercise, "go", GenerationMode.GENERATE))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("exerciseGenerationCapacityExceeded")).withMessageContaining("busy");
        assertThat(publishAttempts.get()).isEqualTo(1);

        assertThat(service.getStatus(owner, exercise)).isEmpty();

        reject.set(false);
        String jobId = service.startJob(owner, exercise, "retry", GenerationMode.GENERATE);
        assertThat(jobId).isNotBlank();
        assertThat(service.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo(jobId);
            assertThat(status.running()).isTrue();
        });
    }

    @Test
    void startJob_whenPublishFailsUnexpectedly_rollsBackSlotAndRethrows() {
        AtomicBoolean fail = new AtomicBoolean(true);
        ApplicationEventPublisher publisher = event -> {
            if (fail.get()) {
                throw new IllegalStateException("misconfigured listener");
            }
        };
        GenerationJobService service = new GenerationJobService(hazelcastInstance, publisher, mock(LLMTokenUsageService.class));
        service.init();
        ProgrammingExercise exercise = exercise(78L);
        User owner = user("owner");

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> service.startJob(owner, exercise, "go", GenerationMode.GENERATE))
                .withMessageContaining("misconfigured");
        assertThat(service.getStatus(owner, exercise)).isEmpty();

        fail.set(false);
        assertThat(service.startJob(owner, exercise, "retry", GenerationMode.GENERATE)).isNotBlank();
    }

    @Test
    void startJob_whenPublishFails_restoresPreviousRetainedReplayState() {
        long exerciseId = 79L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String firstJob = jobService.startJob(owner, exercise, "first", GenerationMode.GENERATE);
        jobService.recordFileChange(exerciseId, firstJob, fileChange("solution/Before.java", "before"));
        jobService.recordEvent(exerciseId, firstJob, ExerciseGenerationEventDTO.done("done", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true), true);
        jobService.clearJob(exerciseId, firstJob);
        assertThat(jobService.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo(firstJob);
            assertThat(status.fileChanges()).singleElement().extracting(ExerciseGenerationFileChangeDTO::path).isEqualTo("solution/Before.java");
        });
        GenerationJobService failingService = new GenerationJobService(hazelcastInstance, event -> {
            throw new IllegalStateException("publish failed");
        }, mock(LLMTokenUsageService.class));
        failingService.init();

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> failingService.startJob(owner, exercise, "second", GenerationMode.GENERATE));

        assertThat(failingService.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo(firstJob);
            assertThat(status.running()).isFalse();
            assertThat(status.fileChanges()).singleElement().extracting(ExerciseGenerationFileChangeDTO::path).isEqualTo("solution/Before.java");
        });
    }

    @Test
    void rollbackUnpublishedStart_doesNotOverwriteNewerReplayState() {
        long exerciseId = 80L;
        String key = String.valueOf(exerciseId);
        Instant now = Instant.now();
        GenerationJobService.JobInfo failedJob = new GenerationJobService.JobInfo("failed", "owner", exerciseId, now, null, "node-a", now, true, null);
        GenerationJobService.JobTranscript failedTranscript = new GenerationJobService.JobTranscript("failed", "owner", exerciseId, GenerationMode.GENERATE,
                new CopyOnWriteArrayList<>(), false);
        GenerationJobService.JobFileChangeIndex failedIndex = new GenerationJobService.JobFileChangeIndex("failed", "owner", List.of(fileChange("solution/Failed.java", "failed")));
        GenerationJobService.JobTranscript previousTranscript = new GenerationJobService.JobTranscript("previous", "owner", exerciseId, GenerationMode.GENERATE,
                new CopyOnWriteArrayList<>(), true);
        GenerationJobService.JobFileChangeIndex previousIndex = new GenerationJobService.JobFileChangeIndex("previous", "owner",
                List.of(fileChange("solution/Previous.java", "previous")));
        GenerationJobService.JobInfo newerJob = new GenerationJobService.JobInfo("newer", "owner", exerciseId, now.plusMillis(1), null, "node-a", now.plusMillis(1), true, null);
        GenerationJobService.JobTranscript newerTranscript = new GenerationJobService.JobTranscript("newer", "owner", exerciseId, GenerationMode.ADAPT,
                new CopyOnWriteArrayList<>(), false);
        GenerationJobService.JobFileChangeIndex newerIndex = new GenerationJobService.JobFileChangeIndex("newer", "owner", List.of(fileChange("solution/Newer.java", "newer")));
        jobMap().set(key, newerJob);
        transcriptMap().set(key, newerTranscript);
        fileChangeMap().set(key, newerIndex);

        ReflectionTestUtils.invokeMethod(jobService, "rollbackUnpublishedStart", exerciseId, key, failedJob, failedTranscript, failedIndex, previousTranscript, previousIndex);

        assertThat(transcriptMap().get(key)).isEqualTo(newerTranscript);
        assertThat(fileChangeMap().get(key)).isEqualTo(newerIndex);
    }

    @Test
    void getStatus_doesNotObserveAnUnpublishedRollbackHalfwayThrough() throws Exception {
        long exerciseId = 81L;
        String key = String.valueOf(exerciseId);
        Instant now = Instant.now();
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        GenerationJobService.JobInfo failedJob = new GenerationJobService.JobInfo("failed", "owner", exerciseId, now, null, "node-a", now, true, null);
        GenerationJobService.JobTranscript failedTranscript = new GenerationJobService.JobTranscript("failed", "owner", exerciseId, GenerationMode.GENERATE,
                new CopyOnWriteArrayList<>(), false);
        GenerationJobService.JobFileChangeIndex failedIndex = new GenerationJobService.JobFileChangeIndex("failed", "owner", List.of());
        GenerationJobService.JobTranscript previousTranscript = new GenerationJobService.JobTranscript("previous", "owner", exerciseId, GenerationMode.ADAPT,
                new CopyOnWriteArrayList<>(), true);
        GenerationJobService.JobFileChangeIndex previousIndex = new GenerationJobService.JobFileChangeIndex("previous", "owner", List.of());
        jobMap().set(key, failedJob);
        transcriptMap().set(key, failedTranscript);
        fileChangeMap().set(key, failedIndex);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean transcriptLockHeld = new AtomicBoolean(true);

        transcriptMap().lock(key);
        try {
            Future<?> rollback = executor.submit(() -> ReflectionTestUtils.invokeMethod(jobService, "rollbackUnpublishedStart", exerciseId, key, failedJob, failedTranscript,
                    failedIndex, previousTranscript, previousIndex));
            await().atMost(Duration.ofSeconds(5)).until(() -> jobMap().get(key) == null);
            Future<Optional<ExerciseGenerationStatusDTO>> status = executor.submit(() -> jobService.getStatus(owner, exercise));

            assertThatThrownBy(() -> status.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            transcriptMap().unlock(key);
            transcriptLockHeld.set(false);
            assertThat(rollback.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(status.get(5, TimeUnit.SECONDS)).hasValueSatisfying(value -> assertThat(value.jobId()).isEqualTo("previous"));
        }
        finally {
            if (transcriptLockHeld.get()) {
                transcriptMap().unlock(key);
            }
            executor.shutdownNow();
        }
    }
}
