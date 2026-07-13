package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
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
    void discardRetainedRun_removesOnlyTheMatchingTranscriptAndSnapshots() {
        long exerciseId = 45L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "adapt", GenerationMode.ADAPT);
        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/A.java", "adapted"));
        jobService.clearJob(exerciseId, jobId);

        jobService.discardRetainedRun(exerciseId, "different-job");
        assertThat(jobService.getStatus(owner, exercise)).isPresent();

        jobService.discardRetainedRun(exerciseId, jobId);

        assertThat(jobService.getStatus(owner, exercise)).isEmpty();
        assertThat(perFileEntries()).isEmpty();
        assertThat(snapshotIndexMap().isEmpty()).isTrue();
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
    void recordSnapshot_refreshesTheActiveJobSlotTtl_soSnapshotHeavyRunsKeepSingleFlight() throws Exception {
        ProgrammingExercise exercise = exercise(33L);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        String key = String.valueOf(exercise.getId());
        jobMap.set(key, jobMap.get(key), 1, TimeUnit.SECONDS);

        jobService.recordSnapshot(33L, jobId, snapshot("solution/Heartbeat.java", "changed"));

        await().pollDelay(Duration.ofMillis(1300)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.startJob(owner, exercise, "again", GenerationMode.GENERATE)));
    }

    @Test
    void recordSnapshot_assignsCrashSafeTtlToSnapshotAndIndexWrites() {
        long exerciseId = 35L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);
        String snapshotKey = GenerationJobService.fileKey(exerciseId, jobId, "solution/Heartbeat.java");

        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/Heartbeat.java", "changed"));

        assertThat(perFileSnapshotMap().getEntryView(snapshotKey).getExpirationTime()).isPositive();
        assertThat(snapshotIndexMap().getEntryView(String.valueOf(exerciseId)).getExpirationTime()).isPositive();
    }

    @Test
    void recordSnapshot_refreshesRetainedTranscriptAndSnapshotIndexTtl_soReconnectPreviewSurvivesLongRuns() {
        long exerciseId = 34L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        String key = String.valueOf(exerciseId);
        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/Heartbeat.java", "initial"));

        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobTranscript> transcriptMap = (IMap<String, GenerationJobService.JobTranscript>) ReflectionTestUtils.getField(jobService,
                "transcriptMap");
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobFileSnapshotIndex> snapshotIndexMap = (IMap<String, GenerationJobService.JobFileSnapshotIndex>) ReflectionTestUtils
                .getField(jobService, "snapshotIndexMap");
        String snapshotKey = GenerationJobService.fileKey(exerciseId, jobId, "solution/Heartbeat.java");
        transcriptMap.set(key, transcriptMap.get(key), 1, TimeUnit.SECONDS);
        snapshotIndexMap.set(key, snapshotIndexMap.get(key), 1, TimeUnit.SECONDS);
        perFileSnapshotMap().setTtl(snapshotKey, 1, TimeUnit.SECONDS);

        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/Heartbeat.java", "fresh"));

        await().pollDelay(Duration.ofMillis(1300)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            ExerciseGenerationStatusDTO status = jobService.getStatus(owner, exercise).orElseThrow();
            assertThat(status.fileSnapshots()).singleElement().satisfies(snapshot -> {
                assertThat(snapshot.path()).isEqualTo("solution/Heartbeat.java");
                assertThat(snapshot.content()).isEqualTo("fresh");
            });
        });
    }

    @Test
    void recordEvent_refreshesRetainedSnapshotTtl_soReconnectPreviewSurvivesProgressOnlyPeriods() {
        long exerciseId = 35L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        String key = String.valueOf(exerciseId);
        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/Preview.java", "kept"));

        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobFileSnapshotIndex> snapshotIndexMap = (IMap<String, GenerationJobService.JobFileSnapshotIndex>) ReflectionTestUtils
                .getField(jobService, "snapshotIndexMap");
        String snapshotKey = GenerationJobService.fileKey(exerciseId, jobId, "solution/Preview.java");
        snapshotIndexMap.set(key, snapshotIndexMap.get(key), 1, TimeUnit.SECONDS);
        perFileSnapshotMap().setTtl(snapshotKey, 1, TimeUnit.SECONDS);

        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);

        await().pollDelay(Duration.ofMillis(1300)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(jobService.getStatus(owner, exercise).orElseThrow().fileSnapshots())
                .singleElement().extracting(ExerciseGenerationFileSnapshotDTO::path).isEqualTo("solution/Preview.java"));
    }

    @Test
    void getStatus_forDifferentUser_returnsEmpty_privacyBoundary() {
        ProgrammingExercise exercise = exercise(99L);
        jobService.startJob(user("instructorA"), exercise, "go", GenerationMode.GENERATE);

        assertThat(jobService.getStatus(user("instructorA"), exercise)).isPresent();
        assertThat(jobService.getStatus(user("instructorB"), exercise)).isEmpty();
    }

    @Test
    void requestCancellation_runsCancelHookExactlyOnce_thenRemovesIt() {
        String jobId = jobService.startJob(user("owner"), exercise(11L), "go", GenerationMode.GENERATE);
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);

        assertThat(jobService.requestCancellation(11L, jobId, user("owner"))).isTrue();
        assertThat(hookRuns.get()).isEqualTo(1);
        assertThat(jobService.isCancelled(jobId)).isTrue();

        assertThat(jobService.requestCancellation(11L, jobId, user("owner"))).isTrue();
        assertThat(hookRuns.get()).isEqualTo(1);
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
    void enterNonCancellablePhase_whenCancellationAlreadyRequested_stopsBeforePersistence() {
        long exerciseId = 14L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);

        assertThat(jobService.requestCancellation(exerciseId, jobId, user("owner"))).isTrue();

        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isFalse();
        assertThat(jobService.isCancelled(jobId)).isTrue();
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
    void clearStaleJobs_marksTranscriptDoneAndReleasesSlot() {
        long exerciseId = 125L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        String localNodeId = (String) ReflectionTestUtils.getField(jobService, "localNodeId");
        jobMap.set(String.valueOf(exerciseId), new GenerationJobService.JobInfo(jobId, "owner", exerciseId, Instant.now().minus(Duration.ofMinutes(10)), null, localNodeId,
                Instant.now().minus(Duration.ofMinutes(10)), true, null));
        GenerationJobService shortTimeoutService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofMinutes(30));
        shortTimeoutService.init();

        shortTimeoutService.clearStaleJobs();

        ExerciseGenerationStatusDTO status = shortTimeoutService.getStatus(owner, exercise).orElseThrow();
        assertThat(status.running()).isFalse();
        assertThat(status.events().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(status.events().getLast().message()).contains("heartbeats");
        assertThat(shortTimeoutService.hasActiveJob(exerciseId)).isFalse();
    }

    @Test
    void clearStaleJobs_broadcastsCancellationAndRejectsLateReplayWrites() {
        long exerciseId = 126L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/Before.java", "before"));
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        GenerationJobService scannerNode = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofMinutes(30));
        scannerNode.init();

        scannerNode.clearStaleJobs();
        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.done("late success", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true), true);
        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/After.java", "after"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(hookRuns).hasValue(1));
        ExerciseGenerationStatusDTO status = jobService.getStatus(owner, exercise).orElseThrow();
        assertThat(status.running()).isFalse();
        assertThat(status.events().getLast().message()).contains("heartbeats");
        assertThat(status.fileSnapshots()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.path()).isEqualTo("solution/Before.java");
            assertThat(snapshot.content()).isEqualTo("before");
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
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        GenerationJobService scannerNode = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(1), Duration.ofMinutes(30));
        scannerNode.init();

        scannerNode.clearStaleJobs();

        verify(budgetService).releaseReservation("reservation-127");
        assertThat(scannerNode.hasActiveJob(exerciseId)).isFalse();
    }

    @Test
    void startJob_reclaimsExpiredSlotInsteadOfRejectingUntilScheduledScanRuns() {
        long exerciseId = 127L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String staleJobId = jobService.startJob(owner, exercise, "old", GenerationMode.GENERATE);
        forceJobHeartbeat(exerciseId, staleJobId, Instant.now().minus(Duration.ofMinutes(10)));
        GenerationJobService shortTimeoutService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofMinutes(30));
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
    void staleHeartbeat_doesNotReclaimNonCancellablePersistenceSlot() {
        long exerciseId = 228L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "old", GenerationMode.GENERATE);
        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        GenerationJobService shortTimeoutService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofMinutes(30));
        shortTimeoutService.init();

        shortTimeoutService.clearStaleJobs();

        assertThat(shortTimeoutService.getStatus(owner, exercise)).hasValueSatisfying(status -> assertThat(status.running()).isTrue());
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> shortTimeoutService.startJob(owner, exercise, "new", GenerationMode.GENERATE));
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

    private static void forceJobDeadline(GenerationJobService service, long exerciseId, Instant deadlineAt) {
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(service, "jobMap");
        GenerationJobService.JobInfo existing = jobMap.get(String.valueOf(exerciseId));
        jobMap.set(String.valueOf(exerciseId), new GenerationJobService.JobInfo(existing.jobId(), existing.userLogin(), exerciseId, existing.startedAt(), deadlineAt,
                existing.ownerNodeId(), existing.lastHeartbeatAt(), existing.cancellable(), existing.budgetReservationId()));
    }

    private static ExerciseGenerationFileSnapshotDTO snapshot(String path, String content) {
        return ExerciseGenerationFileSnapshotDTO.of(path, ExerciseGenerationFileSnapshotDTO.ACTION_CREATE, content, 1);
    }

    private IMap<String, ExerciseGenerationFileSnapshotDTO> perFileSnapshotMap() {
        return hazelcastInstance.getMap(GenerationJobService.SNAPSHOT_MAP_NAME);
    }

    private IMap<String, GenerationJobService.JobInfo> jobMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-jobs");
    }

    private IMap<String, GenerationJobService.JobTranscript> transcriptMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-transcripts");
    }

    private IMap<String, GenerationJobService.JobFileSnapshotIndex> snapshotIndexMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-file-snapshot-index");
    }

    /** The per-file store as a plain {@link Map}, so {@code assertThat(...)} resolves the Map overload (an {@code IMap} matches both the Map and Iterable AssertJ overloads). */
    private Map<String, ExerciseGenerationFileSnapshotDTO> perFileEntries() {
        return perFileSnapshotMap();
    }

    @Test
    void recordSnapshot_keysEachFileSeparately_soAWriteTouchesOneKeyNotTheWholeSet() {
        long exerciseId = 501L;
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);

        List<String> addedKeys = new CopyOnWriteArrayList<>();
        List<String> updatedKeys = new CopyOnWriteArrayList<>();
        IMap<String, ExerciseGenerationFileSnapshotDTO> perFileMap = perFileSnapshotMap();
        perFileMap.addEntryListener((EntryAddedListener<String, ExerciseGenerationFileSnapshotDTO>) event -> addedKeys.add(event.getKey()), false);
        perFileMap.addEntryListener((EntryUpdatedListener<String, ExerciseGenerationFileSnapshotDTO>) event -> updatedKeys.add(event.getKey()), false);

        int fileCount = 150;
        for (int i = 0; i < fileCount; i++) {
            jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/File" + i + ".java", "body " + i));
        }
        assertThat((Map<String, ExerciseGenerationFileSnapshotDTO>) perFileMap).hasSize(fileCount);

        String[] expectedKeys = new String[fileCount];
        for (int i = 0; i < fileCount; i++) {
            expectedKeys[i] = GenerationJobService.fileKey(exerciseId, jobId, "solution/File" + i + ".java");
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(addedKeys).hasSize(fileCount));
        assertThat(addedKeys).doesNotHaveDuplicates().containsExactlyInAnyOrder(expectedKeys);

        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/File0.java", "rewritten"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(updatedKeys).containsExactly(GenerationJobService.fileKey(exerciseId, jobId, "solution/File0.java")));
        assertThat(perFileMap.size()).as("a repeat write does not add a new key").isEqualTo(fileCount);

        List<ExerciseGenerationFileSnapshotDTO> replay = jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots();
        assertThat(replay).hasSize(fileCount);
        assertThat(replay.getFirst().path()).isEqualTo("solution/File0.java");
        assertThat(replay.getFirst().content()).isEqualTo("rewritten");
        assertThat(replay).extracting(ExerciseGenerationFileSnapshotDTO::path).endsWith("solution/File" + (fileCount - 1) + ".java");
    }

    @Test
    void recordSnapshot_beyondCap_evictsEldestKeepingWriteOrderAndBoundedKeys() {
        long exerciseId = 502L;
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);

        int total = GenerationJobService.MAX_RETAINED_SNAPSHOT_FILES + 50;
        for (int i = 0; i < total; i++) {
            jobService.recordSnapshot(exerciseId, jobId, snapshot("tests/File" + i + ".java", "body " + i));
        }

        assertThat((Map<String, ExerciseGenerationFileSnapshotDTO>) perFileSnapshotMap()).hasSize(GenerationJobService.MAX_RETAINED_SNAPSHOT_FILES);
        assertThat(perFileEntries()).doesNotContainKey(GenerationJobService.fileKey(exerciseId, jobId, "tests/File0.java"));

        List<ExerciseGenerationFileSnapshotDTO> replay = jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots();
        assertThat(replay).hasSize(GenerationJobService.MAX_RETAINED_SNAPSHOT_FILES);
        assertThat(replay.getFirst().path()).isEqualTo("tests/File50.java");
        assertThat(replay.getLast().path()).isEqualTo("tests/File" + (total - 1) + ".java");
    }

    @Test
    void clearJob_retainsPerFileSnapshotsForTerminalReplayWindow() {
        long exerciseId = 503L;
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);
        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/A.java", "a"));
        jobService.recordSnapshot(exerciseId, jobId, snapshot("template/B.java", "b"));
        assertThat((Map<String, ExerciseGenerationFileSnapshotDTO>) perFileSnapshotMap()).hasSize(2);

        jobService.clearJob(exerciseId, jobId);

        assertThat(perFileEntries()).containsKeys(GenerationJobService.fileKey(exerciseId, jobId, "solution/A.java"),
                GenerationJobService.fileKey(exerciseId, jobId, "template/B.java"));
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots()).extracting(ExerciseGenerationFileSnapshotDTO::path)
                .containsExactly("solution/A.java", "template/B.java");
    }

    @Test
    void startJob_forANewRun_clearsAPreviousRunsLingeringRetainedSnapshots() {
        long exerciseId = 504L;
        User owner = user("owner");
        IMap<String, GenerationJobService.JobFileSnapshotIndex> indexMap = hazelcastInstance.getMap(GenerationJobService.SNAPSHOT_INDEX_MAP_NAME);
        indexMap.set(String.valueOf(exerciseId), new GenerationJobService.JobFileSnapshotIndex("crashed-job", "owner", List.of("solution/Stale.java")));
        perFileSnapshotMap().set(GenerationJobService.fileKey(exerciseId, "crashed-job", "solution/Stale.java"), snapshot("solution/Stale.java", "old"));

        String secondJob = jobService.startJob(owner, exercise(exerciseId), "again", GenerationMode.GENERATE);
        assertThat(perFileEntries()).as("the new run starts from a clean snapshot store")
                .doesNotContainKey(GenerationJobService.fileKey(exerciseId, "crashed-job", "solution/Stale.java"));
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots()).isEmpty();
        jobService.recordSnapshot(exerciseId, secondJob, snapshot("solution/Fresh.java", "new"));
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots()).extracting(ExerciseGenerationFileSnapshotDTO::path)
                .containsExactly("solution/Fresh.java");
    }

    @Test
    void recordSnapshot_forAStaleJobId_isDropped() {
        long exerciseId = 505L;
        User owner = user("owner");
        jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);

        jobService.recordSnapshot(exerciseId, "some-older-job", snapshot("solution/Ignored.java", "x"));

        assertThat(perFileEntries()).doesNotContainKey(GenerationJobService.fileKey(exerciseId, "some-older-job", "solution/Ignored.java"));
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots()).isEmpty();
    }

    @Test
    void getStatus_ignoresPerFileSnapshotsFromPreviousJobWithSamePath() {
        long exerciseId = 506L;
        User owner = user("owner");
        String firstJob = jobService.startJob(owner, exercise(exerciseId), "first", GenerationMode.GENERATE);
        jobService.clearJob(exerciseId, firstJob);

        String secondJob = jobService.startJob(owner, exercise(exerciseId), "second", GenerationMode.GENERATE);
        perFileSnapshotMap().set(GenerationJobService.fileKey(exerciseId, firstJob, "solution/Calculator.java"), snapshot("solution/Calculator.java", "stale"));
        jobService.recordSnapshot(exerciseId, secondJob, snapshot("solution/Calculator.java", "fresh"));

        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots()).singleElement().extracting(ExerciseGenerationFileSnapshotDTO::content)
                .isEqualTo("fresh");
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

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.startJob(owner, exercise, "go", GenerationMode.GENERATE))
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
        jobService.recordSnapshot(exerciseId, firstJob, snapshot("solution/Before.java", "before"));
        jobService.recordEvent(exerciseId, firstJob, ExerciseGenerationEventDTO.done("done", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true), true);
        jobService.clearJob(exerciseId, firstJob);
        assertThat(jobService.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo(firstJob);
            assertThat(status.fileSnapshots()).singleElement().extracting(ExerciseGenerationFileSnapshotDTO::content).isEqualTo("before");
        });
        GenerationJobService failingService = new GenerationJobService(hazelcastInstance, event -> {
            throw new IllegalStateException("publish failed");
        }, mock(LLMTokenUsageService.class));
        failingService.init();

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> failingService.startJob(owner, exercise, "second", GenerationMode.GENERATE));

        assertThat(failingService.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo(firstJob);
            assertThat(status.running()).isFalse();
            assertThat(status.fileSnapshots()).singleElement().extracting(ExerciseGenerationFileSnapshotDTO::content).isEqualTo("before");
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
        GenerationJobService.JobFileSnapshotIndex failedIndex = new GenerationJobService.JobFileSnapshotIndex("failed", "owner", List.of("solution/Failed.java"));
        GenerationJobService.JobTranscript previousTranscript = new GenerationJobService.JobTranscript("previous", "owner", exerciseId, GenerationMode.GENERATE,
                new CopyOnWriteArrayList<>(), true);
        GenerationJobService.JobFileSnapshotIndex previousIndex = new GenerationJobService.JobFileSnapshotIndex("previous", "owner", List.of("solution/Previous.java"));
        GenerationJobService.JobInfo newerJob = new GenerationJobService.JobInfo("newer", "owner", exerciseId, now.plusMillis(1), null, "node-a", now.plusMillis(1), true, null);
        GenerationJobService.JobTranscript newerTranscript = new GenerationJobService.JobTranscript("newer", "owner", exerciseId, GenerationMode.ADAPT,
                new CopyOnWriteArrayList<>(), false);
        GenerationJobService.JobFileSnapshotIndex newerIndex = new GenerationJobService.JobFileSnapshotIndex("newer", "owner", List.of("solution/Newer.java"));
        jobMap().set(key, newerJob);
        transcriptMap().set(key, newerTranscript);
        snapshotIndexMap().set(key, newerIndex);

        ReflectionTestUtils.invokeMethod(jobService, "rollbackUnpublishedStart", exerciseId, key, failedJob, failedTranscript, failedIndex, previousTranscript, previousIndex);

        assertThat(transcriptMap().get(key)).isEqualTo(newerTranscript);
        assertThat(snapshotIndexMap().get(key)).isEqualTo(newerIndex);
    }
}
