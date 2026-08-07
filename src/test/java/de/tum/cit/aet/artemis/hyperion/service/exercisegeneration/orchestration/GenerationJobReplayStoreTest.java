package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationUsageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerationJobReplayStoreTest {

    /** The production default; the TTL-specific tests inject their own short value instead of waiting this long. */
    private static final Duration TERMINAL_REPLAY_TTL = Duration.ofHours(4);

    private HazelcastInstance hazelcastInstance;

    private GenerationJobReplayStore replayStore;

    @BeforeAll
    void startHazelcast() {
        Config config = new Config();
        config.setClusterName("hyperion-job-replay-store-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @BeforeEach
    void setUp() {
        hazelcastInstance.getDistributedObjects().forEach(distributedObject -> distributedObject.destroy());
        replayStore = new GenerationJobReplayStore(hazelcastInstance, TERMINAL_REPLAY_TTL);
    }

    @AfterAll
    void stopHazelcast() {
        hazelcastInstance.shutdown();
    }

    @Test
    void initializeStart_whenTranscriptInitializationFails_keepsReplayEmpty() {
        long exerciseId = 82L;
        String key = String.valueOf(exerciseId);
        IMap<String, GenerationJobService.JobTranscript> failingTranscriptMap = spy(transcriptMap());
        doThrow(new IllegalStateException("transcript initialization failed")).when(failingTranscriptMap).set(eq(key), any(GenerationJobService.JobTranscript.class));
        ReflectionTestUtils.setField(replayStore, "transcriptMap", failingTranscriptMap);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> replayStore.initializeStart(exerciseId, "failed", "owner", GenerationMode.GENERATE, null))
                .withMessageContaining("transcript initialization failed");

        assertThat(transcriptMap().get(key)).isNull();
        assertThat(fileChangeMap().get(key)).isNull();
    }

    @Test
    void initializeStart_whenFileChangeInitializationFails_restoresPreviousReplay() {
        long exerciseId = 83L;
        String key = String.valueOf(exerciseId);
        GenerationJobService.JobTranscript previousTranscript = transcript("previous", exerciseId, GenerationMode.ADAPT, true);
        GenerationJobService.JobFileChangeIndex previousIndex = fileChangeIndex("previous", "solution/Previous.java");
        transcriptMap().set(key, previousTranscript);
        fileChangeMap().set(key, previousIndex);
        IMap<String, GenerationJobService.JobFileChangeIndex> failingFileChangeMap = spy(fileChangeMap());
        doThrow(new IllegalStateException("file-change initialization failed")).when(failingFileChangeMap).set(eq(key), any(GenerationJobService.JobFileChangeIndex.class));
        ReflectionTestUtils.setField(replayStore, "fileChangeMap", failingFileChangeMap);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> replayStore.initializeStart(exerciseId, "failed", "owner", GenerationMode.GENERATE, null))
                .withMessageContaining("file-change initialization failed");

        assertThat(transcriptMap().get(key)).isEqualTo(previousTranscript);
        assertThat(fileChangeMap().get(key)).isEqualTo(previousIndex);
    }

    @Test
    void restoreUnpublishedStart_doesNotOverwriteNewerReplayState() {
        long exerciseId = 80L;
        String key = String.valueOf(exerciseId);
        GenerationJobService.JobTranscript failedTranscript = transcript("failed", exerciseId, GenerationMode.GENERATE, false);
        GenerationJobService.JobFileChangeIndex failedIndex = fileChangeIndex("failed", "solution/Failed.java");
        GenerationJobService.JobTranscript previousTranscript = transcript("previous", exerciseId, GenerationMode.GENERATE, true);
        GenerationJobService.JobFileChangeIndex previousIndex = fileChangeIndex("previous", "solution/Previous.java");
        GenerationJobService.JobTranscript newerTranscript = transcript("newer", exerciseId, GenerationMode.ADAPT, false);
        GenerationJobService.JobFileChangeIndex newerIndex = fileChangeIndex("newer", "solution/Newer.java");
        transcriptMap().set(key, newerTranscript);
        fileChangeMap().set(key, newerIndex);
        GenerationJobReplayStore.StartedReplay failedReplay = new GenerationJobReplayStore.StartedReplay(failedTranscript, failedIndex, previousTranscript, previousIndex);

        replayStore.restoreUnpublishedStart(exerciseId, failedReplay);

        assertThat(transcriptMap().get(key)).isEqualTo(newerTranscript);
        assertThat(fileChangeMap().get(key)).isEqualTo(newerIndex);
    }

    @Test
    void getStatus_doesNotObserveUnpublishedReplayHalfwayThroughRestore() throws Exception {
        long exerciseId = 81L;
        String key = String.valueOf(exerciseId);
        User owner = user("owner");
        ProgrammingExercise exercise = exercise(exerciseId);
        GenerationJobService.JobTranscript failedTranscript = transcript("failed", exerciseId, GenerationMode.GENERATE, false);
        GenerationJobService.JobFileChangeIndex failedIndex = fileChangeIndex("failed", null);
        GenerationJobService.JobTranscript previousTranscript = transcript("previous", exerciseId, GenerationMode.ADAPT, true);
        GenerationJobService.JobFileChangeIndex previousIndex = fileChangeIndex("previous", null);
        transcriptMap().set(key, failedTranscript);
        fileChangeMap().set(key, failedIndex);
        GenerationJobReplayStore.StartedReplay failedReplay = new GenerationJobReplayStore.StartedReplay(failedTranscript, failedIndex, previousTranscript, previousIndex);
        IMap<String, GenerationJobService.JobInfo> originalJobMap = jobMap();
        IMap<String, GenerationJobService.JobInfo> observedJobMap = spy(originalJobMap);
        CountDownLatch lockAttempts = new CountDownLatch(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        AtomicInteger acquisitions = new AtomicInteger();
        doAnswer(invocation -> {
            lockAttempts.countDown();
            originalJobMap.lock(key);
            if (acquisitions.incrementAndGet() == 1) {
                firstLockAcquired.countDown();
            }
            return null;
        }).when(observedJobMap).lock(key);
        ReflectionTestUtils.setField(replayStore, "jobMap", observedJobMap);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean transcriptLockHeld = new AtomicBoolean(true);

        transcriptMap().lock(key);
        try {
            Future<?> restore = executor.submit(() -> replayStore.restoreUnpublishedStart(exerciseId, failedReplay));
            assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Optional<ExerciseGenerationStatusDTO>> status = executor.submit(() -> replayStore.getStatus(owner, exercise));
            assertThat(lockAttempts.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(restore.isDone()).isFalse();
            assertThat(status.isDone()).isFalse();

            transcriptMap().unlock(key);
            transcriptLockHeld.set(false);
            assertThat(restore.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(status.get(5, TimeUnit.SECONDS)).hasValueSatisfying(value -> assertThat(value.jobId()).isEqualTo("previous"));
        }
        finally {
            if (transcriptLockHeld.get()) {
                transcriptMap().unlock(key);
            }
            executor.shutdownNow();
        }
    }

    @Test
    void retainAfterJobCleared_expiresTranscriptFileChangesAndUsageTogetherOnTheConfiguredTtl() {
        // The TTL is set at three independent call sites, so a short injected value proves all three read the same configured value. It must still outlast the writes below,
        // which already carry it, and the pre-condition reads that follow them.
        GenerationJobReplayStore shortLivedStore = new GenerationJobReplayStore(hazelcastInstance, Duration.ofSeconds(5));
        long exerciseId = 610L;
        String key = String.valueOf(exerciseId);
        String jobId = "short-lived";
        jobMap().set(key, jobInfo(jobId, exerciseId));
        shortLivedStore.initializeStart(exerciseId, jobId, "owner", GenerationMode.GENERATE, null);
        shortLivedStore.recordFileChange(exerciseId, jobId, ExerciseGenerationFileChangeDTO.of("Solution.java", ExerciseGenerationFileChangeDTO.ACTION_WRITE, 1));
        shortLivedStore.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "failed"), true);

        assertThat(transcriptMap().get(key)).isNotNull();
        assertThat(fileChangeMap().get(key)).isNotNull();
        assertThat(usageMap().get(jobId)).isNotNull();
        shortLivedStore.retainAfterJobCleared(exerciseId, jobId);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(transcriptMap().get(key)).as("transcript").isNull();
            assertThat(fileChangeMap().get(key)).as("file changes").isNull();
            assertThat(usageMap().get(jobId)).as("usage").isNull();
        });
    }

    @Test
    void newReplayStore_rejectsANonPositiveTerminalReplayTtl() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new GenerationJobReplayStore(hazelcastInstance, Duration.ZERO))
                .withMessageContaining("terminal-replay-ttl");
    }

    @Test
    void recordEvent_beyondTheRetentionBound_replacesTheDroppedSpanWithOneMarkerThatCountsThemAll() {
        long exerciseId = 611L;
        String key = String.valueOf(exerciseId);
        String jobId = "truncating";
        jobMap().set(key, jobInfo(jobId, exerciseId));
        replayStore.initializeStart(exerciseId, jobId, "owner", GenerationMode.GENERATE, null);
        replayStore.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "HEAD"), false);
        int overflow = 5;
        for (int index = 0; index < GenerationJobReplayStore.MAX_RETAINED_EVENTS - 1 + overflow; index++) {
            replayStore.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, "p" + index), false);
        }

        List<ExerciseGenerationEventDTO> events = transcriptMap().get(key).events();

        assertThat(events).hasSize(GenerationJobReplayStore.MAX_RETAINED_EVENTS);
        assertThat(events.getFirst().message()).isEqualTo("HEAD");
        // The marker occupies a retained slot itself, so the first overflow drops two events.
        assertThat(events.get(1).type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS);
        assertThat(events.get(1).message()).isEqualTo((overflow + 1) + " earlier progress events are no longer retained.");
        assertThat(events.stream().filter(event -> event.message() != null && event.message().endsWith("no longer retained.")).toList()).hasSize(1);
        assertThat(events.get(2).message()).isEqualTo("p" + (overflow + 1));
        assertThat(events.getLast().message()).isEqualTo("p" + (GenerationJobReplayStore.MAX_RETAINED_EVENTS - 2 + overflow));
    }

    @Test
    void recordEvent_withinTheRetentionBound_addsNoTruncationMarker() {
        long exerciseId = 612L;
        String key = String.valueOf(exerciseId);
        String jobId = "not-truncating";
        jobMap().set(key, jobInfo(jobId, exerciseId));
        replayStore.initializeStart(exerciseId, jobId, "owner", GenerationMode.GENERATE, null);
        for (int index = 0; index < GenerationJobReplayStore.MAX_RETAINED_EVENTS; index++) {
            replayStore.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, "p" + index), false);
        }

        List<ExerciseGenerationEventDTO> events = transcriptMap().get(key).events();

        assertThat(events).hasSize(GenerationJobReplayStore.MAX_RETAINED_EVENTS);
        assertThat(events).noneMatch(event -> event.message() != null && event.message().endsWith("no longer retained."));
    }

    @Test
    void recordEvent_terminalSealsAccountingWhenTranscriptCompletes() {
        long exerciseId = 613L;
        String key = String.valueOf(exerciseId);
        String jobId = "sealing";
        jobMap().set(key, jobInfo(jobId, exerciseId));
        replayStore.initializeStart(exerciseId, jobId, "owner", GenerationMode.GENERATE, null);
        replayStore.recordToolCalls(jobId, 3);

        assertThat(replayStore.usageSnapshot(jobId).accountingState()).isEqualTo(ExerciseGenerationAccountingState.PENDING);

        replayStore.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "failed"), true);

        assertThat(transcriptMap().get(key).done()).isTrue();
        assertThat(replayStore.usageSnapshot(jobId).accountingState()).isEqualTo(ExerciseGenerationAccountingState.COMPLETE);
    }

    @Test
    void sealUsageOnWorkerExit_withoutATerminalTranscript_closesTheAccountAsIncompleteInsteadOfLeavingItPending() {
        long exerciseId = 614L;
        String jobId = "no-verdict";
        jobMap().set(String.valueOf(exerciseId), jobInfo(jobId, exerciseId));
        replayStore.initializeStart(exerciseId, jobId, "owner", GenerationMode.GENERATE, null);

        replayStore.sealUsageOnWorkerExit(exerciseId, jobId);

        assertThat(replayStore.usageSnapshot(jobId).accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
    }

    @Test
    void markUsageIncomplete_isAbsorbing_soNoLaterSealCanClaimACompleteAccount() {
        long exerciseId = 615L;
        String jobId = "uncertain";
        jobMap().set(String.valueOf(exerciseId), jobInfo(jobId, exerciseId));
        replayStore.initializeStart(exerciseId, jobId, "owner", GenerationMode.GENERATE, null);

        replayStore.markUsageIncomplete(jobId);
        replayStore.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "failed"), true);
        replayStore.sealUsageOnWorkerExit(exerciseId, jobId);

        assertThat(replayStore.usageSnapshot(jobId).accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
    }

    @Test
    void failedAggregateWrite_cannotBecomeCompleteAfterTheStoreRecovers() {
        long exerciseId = 617L;
        String jobId = "aggregate-write-failed";
        jobMap().set(String.valueOf(exerciseId), jobInfo(jobId, exerciseId));
        replayStore.initializeStart(exerciseId, jobId, "owner", GenerationMode.GENERATE, null);
        IMap<String, Object> actualUsageMap = usageMap();
        IMap<String, Object> failingUsageMap = spy(actualUsageMap);
        doThrow(new IllegalStateException("usage write failed")).when(failingUsageMap).set(eq(jobId), any(), anyLong(), eq(TimeUnit.SECONDS));
        ReflectionTestUtils.setField(replayStore, "usageMap", failingUsageMap);

        assertThatCode(() -> replayStore.recordAgentTurn(jobId)).doesNotThrowAnyException();
        ReflectionTestUtils.setField(replayStore, "usageMap", actualUsageMap);
        replayStore.sealUsage(jobId);

        assertThat(replayStore.usageSnapshot(jobId).accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
    }

    @Test
    void recordAgentTurnAndAttempt_accumulateOnTheUsageSnapshot() {
        long exerciseId = 616L;
        String jobId = "counting";
        jobMap().set(String.valueOf(exerciseId), jobInfo(jobId, exerciseId));
        replayStore.initializeStart(exerciseId, jobId, "owner", GenerationMode.GENERATE, null);

        replayStore.recordAttempt(jobId);
        replayStore.recordAgentTurn(jobId);
        replayStore.recordAgentTurn(jobId);
        replayStore.recordAttempt(jobId);
        replayStore.recordAgentTurn(jobId);

        ExerciseGenerationUsageDTO usage = replayStore.usageSnapshot(jobId).usage();
        assertThat(usage).isNotNull();
        assertThat(usage.agentTurns()).isEqualTo(3);
        assertThat(usage.attempts()).isEqualTo(2);
    }

    @Test
    void recordUsage_forAJobWithNoAccumulator_recordsTheSpendAsUnaccountedInsteadOfThrowing() {
        assertThatCode(() -> replayStore.recordUsage("never-registered", llmRequest())).doesNotThrowAnyException();

        GenerationJobReplayStore.UsageSnapshot snapshot = replayStore.usageSnapshot("never-registered");
        assertThat(snapshot.usage()).isNotNull();
        assertThat(snapshot.usage().modelCalls()).isEqualTo(1);
        assertThat(snapshot.usage().inputTokens()).isEqualTo(100);
        assertThat(snapshot.accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
    }

    @Test
    void anUnaccountedAccumulator_canNeverBeSealedAsComplete() {
        replayStore.recordUsage("never-registered", llmRequest());

        replayStore.sealUsage("never-registered");

        assertThat(replayStore.usageSnapshot("never-registered").accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
    }

    @Test
    void recordingOperationsForAnUntrackedJob_neverThrowAndNeverCancelTheRun() {
        assertThatCode(() -> {
            replayStore.recordAttempt("never-registered");
            replayStore.recordAgentTurn("never-registered");
            replayStore.recordToolCalls("never-registered", 4);
        }).doesNotThrowAnyException();

        ExerciseGenerationUsageDTO usage = replayStore.usageSnapshot("never-registered").usage();
        assertThat(usage).isNotNull();
        assertThat(usage.attempts()).isEqualTo(1);
        assertThat(usage.agentTurns()).isEqualTo(1);
        assertThat(usage.toolCalls()).isEqualTo(4);
    }

    @Test
    void completenessTransitionsNeverConjureAnAccumulatorForAJobThatHasNone() {
        replayStore.sealUsage("discarded");
        replayStore.markUsageIncomplete("discarded");
        replayStore.sealUsageIncomplete("discarded");

        assertThat(usageMap().get("discarded")).isNull();
        assertThat(replayStore.usageSnapshot("discarded")).isEqualTo(new GenerationJobReplayStore.UsageSnapshot(null, ExerciseGenerationAccountingState.INCOMPLETE));
    }

    @Test
    void recordUsage_appliesTheRetentionBound() {
        GenerationJobReplayStore shortLivedStore = new GenerationJobReplayStore(hazelcastInstance, Duration.ofSeconds(1));

        shortLivedStore.recordUsage("orphan", llmRequest());
        assertThat(usageMap().get("orphan")).isNotNull();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(usageMap().get("orphan")).isNull());
    }

    private static LLMRequest llmRequest() {
        return new LLMRequest("model", 100, 1f, 50, 2f, "pipeline", "provider-id", 20L, 0.1f, true);
    }

    private static GenerationJobService.JobInfo jobInfo(String jobId, long exerciseId) {
        return new GenerationJobService.JobInfo(jobId, "owner", exerciseId, Instant.now(), null, "node", null, true, null);
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

    private static GenerationJobService.JobTranscript transcript(String jobId, long exerciseId, GenerationMode mode, boolean done) {
        return new GenerationJobService.JobTranscript(jobId, "owner", exerciseId, mode, List.of(), done, null, null);
    }

    private static GenerationJobService.JobFileChangeIndex fileChangeIndex(String jobId, String path) {
        List<ExerciseGenerationFileChangeDTO> changes = path == null ? List.of()
                : List.of(ExerciseGenerationFileChangeDTO.of(path, ExerciseGenerationFileChangeDTO.ACTION_WRITE, 1));
        return new GenerationJobService.JobFileChangeIndex(jobId, "owner", changes);
    }

    private IMap<String, GenerationJobService.JobInfo> jobMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-jobs");
    }

    private IMap<String, GenerationJobService.JobTranscript> transcriptMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-transcripts");
    }

    private IMap<String, GenerationJobService.JobFileChangeIndex> fileChangeMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-file-changes");
    }

    private IMap<String, Object> usageMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-usage");
    }
}
