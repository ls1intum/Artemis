package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationArtifactCompleteness;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedArtifactsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedFileDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationUsageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
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
        jobService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
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
    void terminalStatusIncludesTransientCompleteJobUsageOnlyForOwner() {
        LLMTokenUsageService tokenUsageService = mock(LLMTokenUsageService.class);
        GenerationJobService meteredJobService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, tokenUsageService);
        meteredJobService.init();
        ProgrammingExercise exercise = exercise(47L);
        User owner = user("owner");
        doAnswer(invocation -> {
            Consumer<LLMRequest> observer = invocation.getArgument(4);
            observer.accept(new LLMRequest("model", 100, 1f, 50, 2f, "pipeline", "provider-id", 20L, 0.1f, true));
            return true;
        }).when(tokenUsageService).trackChatResponseTokenUsage(any(), any(), anyString(), any(), any());

        String jobId = meteredJobService.startJob(owner, exercise, "do it", GenerationMode.GENERATE);
        meteredJobService.tokenUsageSink(null, exercise.getId(), null, jobId).accept(mock(ChatResponse.class));
        meteredJobService.recordToolCalls(jobId, 2);
        meteredJobService.recordEvent(exercise.getId(), jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "failed"), true);
        meteredJobService.clearJob(exercise.getId(), jobId);

        ExerciseGenerationStatusDTO result = meteredJobService.getStatus(owner, exercise).orElseThrow();

        assertThat(result.usage()).isEqualTo(new ExerciseGenerationUsageDTO(1, 2, 0, 0, 100, 50, 20, true, 0.000182, true, List.of("model"), List.of("provider-id"), true));
        assertThat(result.accountingState()).isEqualTo(ExerciseGenerationAccountingState.COMPLETE);

        User other = user("other");
        assertThat(meteredJobService.getStatus(other, exercise)).hasValueSatisfying(status -> {
            assertThat(status.usage()).isNull();
            assertThat(status.accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
        });
    }

    @Test
    void transientAccountingStaysIncompleteAfterAnUncertainProviderAttempt() {
        ProgrammingExercise exercise = exercise(48L);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "do it", GenerationMode.GENERATE);

        jobService.markTokenAccountingIncomplete(jobId);
        jobService.recordEvent(exercise.getId(), jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "failed"), true);
        jobService.sealTokenAccountingOnWorkerExit(exercise.getId(), jobId);

        assertThat(jobService.getStatus(owner, exercise))
                .hasValueSatisfying(status -> assertThat(status.accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE));
    }

    @Test
    void providerRetriesMakeRunLevelAccountingIncompleteFromTheStart() {
        GenerationJobService retryingProviderService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), null, Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run, 1, Duration.ofHours(4), false);
        retryingProviderService.init();
        ProgrammingExercise exercise = exercise(49L);
        User owner = user("owner");

        String jobId = retryingProviderService.startJob(owner, exercise, "do it", GenerationMode.GENERATE);
        retryingProviderService.recordEvent(exercise.getId(), jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "failed"), true);

        assertThat(retryingProviderService.getStatus(owner, exercise))
                .hasValueSatisfying(status -> assertThat(status.accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE));
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
    void claimExternalMutationSlot_blocksGenerationUntilReleased() {
        ProgrammingExercise exercise = exercise(440L);

        String token = jobService.claimExternalMutationSlot(exercise.getId());

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.startJob(user("owner"), exercise, "generate", GenerationMode.GENERATE));
        jobService.clearExternalMutationSlot(exercise.getId(), token);
        assertThat(jobService.startJob(user("owner"), exercise, "generate", GenerationMode.GENERATE)).isNotBlank();
    }

    @Test
    void getStatus_doesNotExposeAnExternalMutationAsExerciseGeneration() {
        ProgrammingExercise exercise = exercise(441L);
        jobService.claimExternalMutationSlot(exercise.getId());

        assertThat(jobService.getStatus(user("owner"), exercise)).isEmpty();
    }

    @Test
    void activeGenerationSlot_doesNotExpireForSupportedLongRunningJobs() {
        long exerciseId = 447L;
        GenerationJobService longRunningJobService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofHours(3).plusMinutes(1), Duration.ofHours(3));
        longRunningJobService.init();

        String jobId = longRunningJobService.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = jobMap();
        assertThat(jobMap.getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);

        assertThat(longRunningJobService.heartbeat(exerciseId, jobId)).isTrue();
        assertThat(jobMap.getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);

        assertThat(longRunningJobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
        assertThat(jobMap.getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);

        longRunningJobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        longRunningJobService.recordFileChange(exerciseId, jobId, fileChange("solution/Preview.java"));
        assertThat(jobMap.getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);
        assertThat(transcriptMap().getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(GenerationJobService.DEFAULT_TERMINAL_REPLAY_TTL.toMillis());
        assertThat(fileChangeMap().getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(GenerationJobService.DEFAULT_TERMINAL_REPLAY_TTL.toMillis());
    }

    @Test
    void shippedTerminalReplayTtl_outlastsTheShippedMaximumJobDuration() throws IOException {
        // Read from the shipped configuration rather than from the constant under test, so raising max-job-duration without raising the retention window fails here. The test
        // classpath shadows config/application-artemis.yml, so the shipped copy is selected by content: the one that declares the retention window.
        List<PropertySource<?>> shippedConfiguration = artemisConfigurationDeclaring("artemis.hyperion.generation.terminal-replay-ttl");
        Duration shippedTerminalReplayTtl = shippedDuration(shippedConfiguration, "artemis.hyperion.generation.terminal-replay-ttl");
        Duration shippedMaxJobDuration = shippedDuration(shippedConfiguration, "artemis.hyperion.agent.max-job-duration");

        assertThat(shippedTerminalReplayTtl).isEqualTo(GenerationJobService.DEFAULT_TERMINAL_REPLAY_TTL);
        assertThat(shippedTerminalReplayTtl).isGreaterThan(shippedMaxJobDuration.multipliedBy(2));
    }

    private static List<PropertySource<?>> artemisConfigurationDeclaring(String property) throws IOException {
        List<PropertySource<?>> declaring = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:config/application-artemis.yml")) {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("artemis-config", resource);
            if (sources.stream().anyMatch(source -> source.getProperty(property) != null)) {
                declaring.addAll(sources);
            }
        }
        assertThat(declaring).as("exactly one classpath copy of config/application-artemis.yml declares %s", property).isNotEmpty();
        return declaring;
    }

    private static Duration shippedDuration(List<PropertySource<?>> sources, String property) {
        return sources.stream().map(source -> source.getProperty(property)).filter(Objects::nonNull).findFirst().map(value -> Duration.parse(String.valueOf(value)))
                .orElseThrow(() -> new AssertionError(property + " is not declared in the shipped configuration"));
    }

    @Test
    void activeCancellationMarker_doesNotExpireBeforeTheJobClears() {
        long exerciseId = 448L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);

        assertThat(jobService.requestCancellation(exerciseId, jobId, user("owner"))).isTrue();
        @SuppressWarnings("unchecked")
        IMap<String, Boolean> cancellationMap = hazelcastInstance.getMap("hyperion-exercise-generation-cancellations");
        assertThat(cancellationMap.getEntryView(jobId).getTtl()).isEqualTo(Duration.ofMinutes(30).toMillis());
        assertThat(transcriptMap().getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(GenerationJobService.DEFAULT_TERMINAL_REPLAY_TTL.toMillis());

        jobService.clearJob(exerciseId, jobId);
        assertThat(cancellationMap.get(jobId)).isNull();
        assertThat(transcriptMap().getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(GenerationJobService.DEFAULT_TERMINAL_REPLAY_TTL.toMillis());
    }

    @Test
    void claimSlot_whenDataMemberCountDiffersFromConfiguredTopology_failsClosed() {
        GenerationJobService mismatchedTopologyService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), null, Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run, 2);
        mismatchedTopologyService.init();

        assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> mismatchedTopologyService.rejectIfActiveJobCannotBeReclaimed(446L))
                .withMessageContaining("expected 2").withMessageContaining("observed 1");
        assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> mismatchedTopologyService.claimExternalMutationSlot(446L))
                .withMessageContaining("expected 2").withMessageContaining("observed 1");
        assertThat(mismatchedTopologyService.hasActiveJob(446L)).isFalse();
    }

    @Test
    void claimSlot_rechecksTopologyAfterAcquiringTheExerciseLock() throws Exception {
        long exerciseId = 449L;
        String key = String.valueOf(exerciseId);
        IMap<String, GenerationJobService.JobInfo> jobMap = jobMap();
        HazelcastInstance observedHazelcast = spy(hazelcastInstance);
        Cluster cluster = mock(Cluster.class);
        Member firstMember = hazelcastInstance.getCluster().getLocalMember();
        Member secondMember = mock(Member.class);
        AtomicBoolean topologyChanged = new AtomicBoolean(false);
        when(cluster.getMembers()).thenAnswer(invocation -> topologyChanged.get() ? Set.of(firstMember, secondMember) : Set.of(firstMember));
        when(secondMember.isLiteMember()).thenReturn(false);
        when(cluster.getLocalMember()).thenReturn(firstMember);
        doReturn(cluster).when(observedHazelcast).getCluster();
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(observedHazelcast), event -> {
        }, mock(LLMTokenUsageService.class));
        service.init();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAttempted = captureNextJobLockAttempt(service, key);
        AtomicBoolean lockHeld = new AtomicBoolean(true);

        jobMap.lock(key);
        try {
            Future<String> claim = executor.submit(() -> service.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE));
            assertThat(lockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            topologyChanged.set(true);
            jobMap.unlock(key);
            lockHeld.set(false);

            assertThatThrownBy(() -> claim.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(ServiceUnavailableAlertException.class);
            assertThat(service.hasActiveJob(exerciseId)).isFalse();
        }
        finally {
            if (lockHeld.get()) {
                jobMap.unlock(key);
            }
            executor.shutdownNow();
        }
    }

    /** Interposes on the map to verify the value guard still fails closed if ownership changes unexpectedly. */
    @Test
    void claimSlot_failsClosedWhenAnotherNodeFilledTheSlotAfterTheEmptyRead() {
        long exerciseId = 452L;
        String key = String.valueOf(exerciseId);
        IMap<String, GenerationJobService.JobInfo> realJobMap = jobMap();
        HazelcastInstance observedHazelcast = spy(hazelcastInstance);
        IMap<String, GenerationJobService.JobInfo> observedJobMap = spy(realJobMap);
        doReturn(observedJobMap).when(observedHazelcast).getMap("hyperion-exercise-generation-jobs");
        // The claimant reads an empty slot; by the time it writes, another owner already owns the exercise.
        doReturn(null).when(observedJobMap).get(key);
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(observedHazelcast), event -> {
        }, mock(LLMTokenUsageService.class));
        service.init();
        jobService.startJob(user("winner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);
        GenerationJobService.JobInfo winner = realJobMap.get(key);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.startJob(user("straggler"), exercise(exerciseId), "generate", GenerationMode.GENERATE));
        assertThat(realJobMap.get(key)).isEqualTo(winner);
    }

    /** The heartbeat is reported lost rather than overwriting whichever job now holds the slot. */
    @Test
    void heartbeat_isReportedLostWhenTheSlotChangedAfterTheOwnershipCheck() {
        long exerciseId = 453L;
        String key = String.valueOf(exerciseId);
        IMap<String, GenerationJobService.JobInfo> realJobMap = jobMap();
        HazelcastInstance observedHazelcast = spy(hazelcastInstance);
        IMap<String, GenerationJobService.JobInfo> observedJobMap = spy(realJobMap);
        doReturn(observedJobMap).when(observedHazelcast).getMap("hyperion-exercise-generation-jobs");
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(observedHazelcast), event -> {
        }, mock(LLMTokenUsageService.class));
        service.init();
        String jobId = service.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);
        GenerationJobService.JobInfo owned = realJobMap.get(key);
        // The ownership check still sees this node's job, but the slot has since been replaced.
        doReturn(owned).when(observedJobMap).get(key);
        GenerationJobService.JobInfo replacement = owned.withHeartbeat(Instant.now().minusSeconds(1));
        realJobMap.set(key, replacement);

        assertThat(service.heartbeat(exerciseId, jobId)).isFalse();
        assertThat(realJobMap.get(key)).isEqualTo(replacement);
    }

    @Test
    void clearStaleJobs_keepsExternalMutationAfterOwnerLeavesClusterUntilValueGuardedRecovery() {
        long exerciseId = 445L;
        String token = jobService.claimExternalMutationSlot(exerciseId);
        forceJobHeartbeat(exerciseId, token, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        GenerationJobService scanner = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofSeconds(30));
        scanner.init();

        scanner.clearStaleJobs();

        assertThat(scanner.hasActiveJob(exerciseId)).isTrue();
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> scanner.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE));
        assertThat(scanner.getWedgedSlotInfo(exerciseId)).hasValueSatisfying(info -> {
            assertThat(info.token()).isEqualTo(token);
            assertThat(info.ownerNodeId()).isEqualTo("departed-node");
        });
        assertThat(scanner.recoverWedgedSlot(exerciseId, "external-mutation-wrong")).isFalse();
        assertThat(scanner.hasActiveJob(exerciseId)).isTrue();
        assertThat(scanner.recoverWedgedSlot(exerciseId, token)).isTrue();
        assertThat(scanner.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE)).isNotBlank();
    }

    /**
     * A generation that dies inside the non-cancellable persistence phase is never reclaimed by the stale-job scan, and the slot it holds is the same one ordinary REST edits
     * claim, so without recovery the exercise stays un-generatable, un-revertable and un-editable on a map with no TTL until the cluster restarts.
     */
    @Test
    void recoverWedgedSlot_releasesAGenerationSlotStuckInThePersistencePhaseAfterItsOwnerLeftTheCluster() {
        long exerciseId = 460L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);
        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
        forceJobOwner(exerciseId, "departed-node");

        // No automatic or interactive lever releases this slot, which is what makes the operator path necessary.
        jobService.clearStaleJobs();
        assertThat(jobService.hasActiveJob(exerciseId)).isTrue();
        assertThat(jobService.requestSystemCancellation(exerciseId, jobId)).isFalse();

        assertThat(jobService.getWedgedSlotInfo(exerciseId)).hasValueSatisfying(info -> {
            assertThat(info.token()).isEqualTo(jobId);
            assertThat(info.kind()).isEqualTo(GenerationJobService.WedgedSlotKind.GENERATION);
            assertThat(info.ownerNodeId()).isEqualTo("departed-node");
            assertThat(info.ownerLeftCluster()).isTrue();
        });
        assertThat(jobService.recoverWedgedSlot(exerciseId, "not-the-token")).isFalse();
        assertThat(jobService.hasActiveJob(exerciseId)).isTrue();

        assertThat(jobService.recoverWedgedSlot(exerciseId, jobId)).isTrue();

        // Usable again on all three axes the wedged slot blocked.
        assertThat(jobService.getWedgedSlotInfo(exerciseId)).isEmpty();
        String mutationToken = jobService.claimExternalMutationSlot(exerciseId);
        jobService.clearExternalMutationSlot(exerciseId, mutationToken);
        String revertToken = jobService.claimRevertSlot(user("owner"), exerciseId);
        jobService.clearRevertSlot(exerciseId, revertToken);
        assertThat(jobService.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE)).isNotBlank();
    }

    /** A recovered generation run must be reported as over, not left polling as running forever with the instructor told nothing. */
    @Test
    void recoverWedgedSlot_terminalizesTheGenerationRunItReleases() {
        long exerciseId = 461L;
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise(exerciseId), "generate", GenerationMode.GENERATE);
        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
        forceJobOwner(exerciseId, "departed-node");

        assertThat(jobService.recoverWedgedSlot(exerciseId, jobId)).isTrue();

        assertThat(jobService.getStatus(owner, exercise(exerciseId))).hasValueSatisfying(status -> {
            assertThat(status.running()).isFalse();
            assertThat(status.jobId()).isEqualTo(jobId);
        });
    }

    /** Cluster departure alone never justifies recovery, so a live owner must keep its slot even for a non-cancellable generation. */
    @Test
    void recoverWedgedSlot_refusesAGenerationSlotWhoseOwnerIsStillAClusterMember() {
        long exerciseId = 462L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);
        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();

        assertThat(jobService.recoverWedgedSlot(exerciseId, jobId)).isFalse();
        assertThat(jobService.hasActiveJob(exerciseId)).isTrue();
    }

    @Test
    void recoverWedgedSlot_releasesAWedgedRevertSlot() {
        long exerciseId = 463L;
        String token = jobService.claimRevertSlot(user("owner"), exerciseId);
        forceJobOwner(exerciseId, "departed-node");

        assertThat(jobService.getWedgedSlotInfo(exerciseId)).hasValueSatisfying(info -> assertThat(info.kind()).isEqualTo(GenerationJobService.WedgedSlotKind.REVERT));
        assertThat(jobService.recoverWedgedSlot(exerciseId, token)).isTrue();
        assertThat(jobService.hasActiveJob(exerciseId)).isFalse();
    }

    /** Diagnosis must not offer a healthy, still-cancellable run to an operator as something to yank. */
    @Test
    void getWedgedSlotInfo_isEmptyForARunningCancellableGeneration() {
        long exerciseId = 464L;
        jobService.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);

        assertThat(jobService.getWedgedSlotInfo(exerciseId)).isEmpty();
    }

    /**
     * A minority island must never recover: the owner may merely be partitioned away rather than stopped. The two-member case is a minority once one member is lost, which is
     * inherent to the majority quorum these maps are configured with — a two-node deployment cannot self-recover. The surviving-majority case needs two real members and lives in
     * {@code GenerationJobServiceClusterTest}.
     */
    @ParameterizedTest
    @ValueSource(ints = { 2, 3, 5 })
    void recoverWedgedSlot_failsClosedWhenTheVisibleMembersAreNotAMajority(int expectedDataMemberCount) {
        long exerciseId = 465L + expectedDataMemberCount;
        String token = jobService.claimExternalMutationSlot(exerciseId);
        forceJobOwner(exerciseId, "departed-node");
        GenerationJobService island = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), null, Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run, expectedDataMemberCount);
        island.init();

        assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> island.recoverWedgedSlot(exerciseId, token)).withMessageContaining("majority");
        assertThat(jobService.hasActiveJob(exerciseId)).isTrue();
    }

    @Test
    void claimExternalMutationSlot_whenGenerationOwnsSlot_rejectsMutation() {
        ProgrammingExercise exercise = exercise(441L);
        jobService.startJob(user("owner"), exercise, "generate", GenerationMode.GENERATE);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.claimExternalMutationSlot(exercise.getId())).withMessageContaining("generation");
    }

    @Test
    void startAndClearJob_publishPublicExerciseStateWithoutPrivateTranscript() {
        List<Object> publishedEvents = new CopyOnWriteArrayList<>();
        GenerationJobService publishingService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), publishedEvents::add,
                mock(LLMTokenUsageService.class));
        publishingService.init();
        ProgrammingExercise exercise = exercise(442L);

        String jobId = publishingService.startJob(user("owner"), exercise, "private prompt", GenerationMode.GENERATE);
        publishingService.clearJob(exercise.getId(), jobId);

        assertThat(publishedEvents).filteredOn(ExerciseGenerationStateChangedEvent.class::isInstance).containsExactly(
                new ExerciseGenerationStateChangedEvent(new ExerciseGenerationStateDTO(exercise.getId(), jobId, true)),
                new ExerciseGenerationStateChangedEvent(new ExerciseGenerationStateDTO(exercise.getId(), jobId, false)));
    }

    @Test
    void startJob_carriesOriginalSourceBriefToTheWorkerWithoutPersistence() {
        List<Object> publishedEvents = new CopyOnWriteArrayList<>();
        GenerationJobService publishingService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), publishedEvents::add,
                mock(LLMTokenUsageService.class));
        publishingService.init();
        ProgrammingExercise exercise = exercise(443L);

        publishingService.startJob(user("owner"), exercise, "resolved authoring instruction", GenerationMode.GENERATE, null, "original instructor brief");

        assertThat(publishedEvents).filteredOn(GenerationStartedEvent.class::isInstance).singleElement().satisfies(event -> {
            GenerationStartedEvent started = (GenerationStartedEvent) event;
            assertThat(started.userPrompt()).isEqualTo("resolved authoring instruction");
            assertThat(started.sourceBrief()).isEqualTo("original instructor brief");
        });
    }

    @Test
    void startJob_publishesRunningStateBeforeDispatchingTheWorker() {
        List<ExerciseGenerationStateChangedEvent> stateEvents = new CopyOnWriteArrayList<>();
        AtomicReference<GenerationJobService> serviceReference = new AtomicReference<>();
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof ExerciseGenerationStateChangedEvent stateEvent) {
                stateEvents.add(stateEvent);
            }
            else if (event instanceof GenerationStartedEvent startedEvent) {
                serviceReference.get().clearJob(startedEvent.exercise().getId(), startedEvent.jobId());
            }
        };
        GenerationJobService publishingService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), publisher,
                mock(LLMTokenUsageService.class));
        serviceReference.set(publishingService);
        publishingService.init();
        ProgrammingExercise exercise = exercise(444L);

        String jobId = publishingService.startJob(user("owner"), exercise, "finish immediately", GenerationMode.GENERATE);

        assertThat(stateEvents).containsExactly(new ExerciseGenerationStateChangedEvent(new ExerciseGenerationStateDTO(exercise.getId(), jobId, true)),
                new ExerciseGenerationStateChangedEvent(new ExerciseGenerationStateDTO(exercise.getId(), jobId, false)));
    }

    @Test
    void discardRetainedRun_removesOnlyTheMatchingTranscriptAndFileChanges() {
        long exerciseId = 45L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "adapt", GenerationMode.ADAPT);
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/A.java"));
        jobService.clearJob(exerciseId, jobId);

        jobService.discardRetainedRun(exerciseId, "different-job");
        assertThat(jobService.getStatus(owner, exercise)).isPresent();

        jobService.discardRetainedRun(exerciseId, jobId);

        assertThat(jobService.getStatus(owner, exercise)).isEmpty();
        assertThat(fileChangeMap().isEmpty()).isTrue();
    }

    @Test
    void discardRetainedRun_waitsForTheJobMutex() throws Exception {
        long exerciseId = 46L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "adapt", GenerationMode.ADAPT);
        jobService.clearJob(exerciseId, jobId);
        String key = String.valueOf(exerciseId);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAttempted = captureNextJobLockAttempt(key);
        Future<?> discard;

        jobMap().lock(key);
        try {
            discard = executor.submit(() -> jobService.discardRetainedRun(exerciseId, jobId));
            assertThat(lockAttempted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(discard.isDone()).isFalse();
            assertThat(transcriptMap().get(key)).isNotNull();
        }
        finally {
            jobMap().unlock(key);
        }
        discard.get(2, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(jobService.getStatus(owner, exercise)).isEmpty();
    }

    @Test
    void retainUnsavedArtifacts_makesTheCandidateReadableByTheOwningUser() {
        long exerciseId = 600L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        ExerciseGenerationRetainedArtifactsDTO artifacts = retainedArtifacts(jobId);

        jobService.retainUnsavedArtifacts(exerciseId, jobId, owner.getLogin(), artifacts);

        assertThat(jobService.getRetainedArtifacts(owner, exercise)).contains(artifacts);
    }

    /** A reclaimed run can still be winding down while its replacement is under way; retaining then would expose its stale draft as the newer run's candidate. */
    @Test
    void retainUnsavedArtifacts_isRefusedForARunTheExerciseHasMovedPast() {
        long exerciseId = 602L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String stragglerJobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.clearJob(exerciseId, stragglerJobId);
        jobService.startJob(owner, exercise, "go again", GenerationMode.GENERATE);

        jobService.retainUnsavedArtifacts(exerciseId, stragglerJobId, owner.getLogin(), retainedArtifacts(stragglerJobId));

        assertThat(jobService.getRetainedArtifacts(owner, exercise)).isEmpty();
    }

    /**
     * The transcript carries the same terminal-replay TTL as the artifact, so a long-delayed worker can arrive after it has expired. Absence of evidence is not evidence that
     * this run is still current, so the write must fail closed rather than repopulate the slot.
     */
    @Test
    void retainUnsavedArtifacts_isRefusedWhenNeitherTranscriptNorActiveJobNamesTheRun() {
        long exerciseId = 603L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.clearJob(exerciseId, jobId);
        // Stands in for the transcript ageing out of the replay store while a superseded worker was still winding down.
        transcriptMap().remove(String.valueOf(exerciseId));

        jobService.retainUnsavedArtifacts(exerciseId, jobId, owner.getLogin(), retainedArtifacts(jobId));

        assertThat(jobService.getRetainedArtifacts(owner, exercise)).isEmpty();
    }

    @Test
    void getRetainedArtifacts_forADifferentUser_isEmpty_ownerOnlyWithNoSanitizedFallback() {
        long exerciseId = 601L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.retainUnsavedArtifacts(exerciseId, jobId, owner.getLogin(), retainedArtifacts(jobId));

        assertThat(jobService.getRetainedArtifacts(user("other"), exercise)).isEmpty();
    }

    @Test
    void retainUnsavedArtifacts_withAnEmptySnapshot_retainsNothing() {
        long exerciseId = 602L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        ExerciseGenerationRetainedArtifactsDTO empty = new ExerciseGenerationRetainedArtifactsDTO(jobId, ExerciseGenerationArtifactCompleteness.COMPLETE, null, null, List.of());

        jobService.retainUnsavedArtifacts(exerciseId, jobId, owner.getLogin(), empty);

        assertThat(jobService.getRetainedArtifacts(owner, exercise)).isEmpty();
    }

    @Test
    void startJob_forANewRunOnTheSameExercise_clearsThePreviousRunsRetainedArtifacts() {
        long exerciseId = 603L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String firstJob = jobService.startJob(owner, exercise, "first", GenerationMode.GENERATE);
        jobService.retainUnsavedArtifacts(exerciseId, firstJob, owner.getLogin(), retainedArtifacts(firstJob));
        jobService.clearJob(exerciseId, firstJob);
        assertThat(jobService.getRetainedArtifacts(owner, exercise)).isPresent();

        jobService.startJob(owner, exercise, "second", GenerationMode.GENERATE);

        assertThat(jobService.getRetainedArtifacts(owner, exercise)).isEmpty();
    }

    @Test
    void discardRetainedRun_removesOnlyTheMatchingRetainedArtifacts() {
        long exerciseId = 604L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.retainUnsavedArtifacts(exerciseId, jobId, owner.getLogin(), retainedArtifacts(jobId));
        jobService.clearJob(exerciseId, jobId);

        jobService.discardRetainedRun(exerciseId, "different-job");
        assertThat(jobService.getRetainedArtifacts(owner, exercise)).isPresent();

        jobService.discardRetainedRun(exerciseId, jobId);
        assertThat(jobService.getRetainedArtifacts(owner, exercise)).isEmpty();
    }

    /** The client is asked to tell an instructor whether there is anything to look at, so the status has to answer that from the retained snapshot rather than assume there is. */
    @Test
    void status_reportsNoRetainedArtifacts_forATerminalRunThatKeptNothing() {
        long exerciseId = 620L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed."), true);
        jobService.clearJob(exerciseId, jobId);

        assertThat(jobService.getStatus(owner, exercise)).get().extracting(ExerciseGenerationStatusDTO::artifactsRetained).isEqualTo(false);
    }

    @Test
    void status_reportsRetainedArtifacts_onceTheRunActuallyRetainedACandidate() {
        long exerciseId = 621L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed."), true);
        jobService.retainUnsavedArtifacts(exerciseId, jobId, owner.getLogin(), retainedArtifacts(jobId));
        jobService.clearJob(exerciseId, jobId);

        assertThat(jobService.getStatus(owner, exercise)).get().extracting(ExerciseGenerationStatusDTO::artifactsRetained).isEqualTo(true);
    }

    /** The retained candidate is owner-only, so the sanitized view another instructor sees must not advertise work it may not read. */
    @Test
    void status_doesNotAdvertiseARetainedCandidateToAnotherInstructor() {
        long exerciseId = 622L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed."), true);
        jobService.retainUnsavedArtifacts(exerciseId, jobId, owner.getLogin(), retainedArtifacts(jobId));
        jobService.clearJob(exerciseId, jobId);

        assertThat(jobService.getStatus(user("other"), exercise)).get().extracting(ExerciseGenerationStatusDTO::artifactsRetained).isEqualTo(false);
        assertThat(jobService.getStatus(owner, exercise)).get().extracting(ExerciseGenerationStatusDTO::artifactsRetained).isEqualTo(true);
    }

    private static ExerciseGenerationRetainedArtifactsDTO retainedArtifacts(String jobId) {
        return new ExerciseGenerationRetainedArtifactsDTO(jobId, ExerciseGenerationArtifactCompleteness.COMPLETE, "Problem statement", "# Spec",
                List.of(new ExerciseGenerationRetainedFileDTO(ExerciseGenerationFileChangeDTO.REPOSITORY_TEMPLATE, "src/Main.java", "public class Main {}")));
    }

    @Test
    void recordEvent_beyondCap_keepsStartedHeadAndAdmitsTheGapItDropped_preservingOrder() {
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
        // 600 progress events follow the retained head, the tail holds 498 of them (the head and the marker occupy the other two slots), so 600 - 498 = 102 were dropped.
        assertThat(events.get(1).message()).isEqualTo("102 earlier progress events are no longer retained.");
        assertThat(events.stream().filter(event -> event.message() != null && event.message().endsWith("no longer retained."))).hasSize(1);
        assertThat(events.get(2).message()).isEqualTo("p" + (overflow - 498));
        assertThat(events.getLast().message()).isEqualTo("p" + (overflow - 1));
    }

    @Test
    void getStatus_carriesExplicitMode_soReconnectRestoresAdaptAffordances() {
        ProgrammingExercise exercise = exercise(31L);
        User owner = user("owner");
        jobService.startJob(owner, exercise, "fix it", GenerationMode.ADAPT);

        ExerciseGenerationStatusDTO status = jobService.getStatus(owner, exercise).orElseThrow();
        assertThat(status.mode()).isEqualTo(GenerationMode.ADAPT);
        assertThat(status.usage()).isNull();
        // Still running: the cost so far is a snapshot, not a total.
        assertThat(status.accountingState()).isEqualTo(ExerciseGenerationAccountingState.PENDING);
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
        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.done("result message", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS,
                new ExerciseGenerationVerdictDTO(true, true, true, 3, List.of()), true, Map.of("solution", "abc123"), 42L), true);
        jobService.clearJob(exerciseId, jobId);

        // Only the mid-run transcript is hidden from a non-owner instructor; the terminal outcome carries the same review identity the owner sees.
        assertThat(jobService.getStatus(user("instructorB"), exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isFalse();
            assertThat(status.ownedByCaller()).isFalse();
            assertThat(status.fileChanges()).isEmpty();
            assertThat(status.events()).singleElement().satisfies(event -> {
                assertThat(event.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
                assertThat(event.message()).isEqualTo("result message");
                assertThat(event.verdict().mechanicallyVerified()).isTrue();
                assertThat(event.liveExerciseChanged()).isTrue();
                assertThat(event.savedRepositoryCommits()).containsEntry("solution", "abc123");
                assertThat(event.savedExerciseVersionId()).isEqualTo(42L);
            });
        });
    }

    @Test
    void getStatus_usesActiveSlotOwnershipWhenRetainedTranscriptBelongsToPreviousRun() {
        long exerciseId = 100L;
        ProgrammingExercise exercise = exercise(exerciseId);
        jobService.startJob(user("previousOwner"), exercise, "first", GenerationMode.GENERATE);
        @SuppressWarnings("unchecked")
        de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo> jobMap = (de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils
                .getField(jobService, "jobMap");
        Instant now = Instant.now();
        jobMap.put(String.valueOf(exerciseId), new GenerationJobService.JobInfo("new-job", "currentowner", exerciseId, now, now.plusSeconds(60), "node", now, false, null));

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

        assertThat(jobService.requestCancellation(11L, jobId, user("owner"))).isTrue();
        assertThat(hookRuns.get()).isEqualTo(1);
    }

    @Test
    void requestCancellation_whenClusterInterruptPublicationFails_stillReturnsSuccess() {
        DistributedDataProvider failingProvider = spy(HyperionDistributedDataTestProvider.provider(hazelcastInstance));
        @SuppressWarnings("unchecked")
        DistributedTopic<Object> failingCancelTopic = mock(DistributedTopic.class);
        doReturn(failingCancelTopic).when(failingProvider).getTopic("hyperion-exercise-generation-cancel-requests");
        doThrow(new IllegalStateException("cancel topic unavailable")).when(failingCancelTopic).publish(any());
        GenerationJobService failingPublishJobService = new GenerationJobService(failingProvider, event -> {
        }, mock(LLMTokenUsageService.class));
        failingPublishJobService.init();
        ProgrammingExercise exercise = exercise(115L);
        User owner = user("owner");
        String jobId = failingPublishJobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);

        assertThat(failingPublishJobService.requestCancellation(exercise.getId(), jobId, owner)).isTrue();
        assertThat(failingPublishJobService.getStatus(owner, exercise).orElseThrow().events()).extracting(ExerciseGenerationEventDTO::type)
                .containsExactly(ExerciseGenerationEventDTO.Type.CANCELLED);
    }

    @Test
    void requestCancellation_publishesTheExactRetainedTerminalEventForLiveClients() {
        AtomicReference<Object> publishedEvent = new AtomicReference<>();
        GenerationJobService publishingJobService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), publishedEvent::set,
                mock(LLMTokenUsageService.class));
        publishingJobService.init();
        ProgrammingExercise exercise = exercise(114L);
        User owner = user("owner");
        String jobId = publishingJobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        publishedEvent.set(null);

        assertThat(publishingJobService.requestCancellation(exercise.getId(), jobId, owner)).isTrue();

        ExerciseGenerationEventDTO retainedEvent = publishingJobService.getStatus(owner, exercise).orElseThrow().events().getLast();
        assertThat(publishedEvent.get()).isEqualTo(new GenerationCancellationEvent(owner.getLogin(), jobId, retainedEvent));
    }

    @Test
    void requestCancellation_dispatchesTheCancelHookWithoutBlockingTheCaller() {
        AtomicInteger submissions = new AtomicInteger();
        AtomicReference<Runnable> submittedCleanup = new AtomicReference<>();
        GenerationJobService asyncJobService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), null, Duration.ofMinutes(35), Duration.ofMinutes(30), task -> {
            submissions.incrementAndGet();
            submittedCleanup.set(task);
        });
        asyncJobService.init();
        String jobId = asyncJobService.startJob(user("owner"), exercise(113L), "go", GenerationMode.GENERATE);
        AtomicInteger hookRuns = new AtomicInteger();
        asyncJobService.registerCancelHook(jobId, hookRuns::incrementAndGet);

        assertThat(asyncJobService.requestCancellation(113L, jobId, user("owner"))).isTrue();
        assertThat(hookRuns).hasValue(0);
        assertThat(submissions).hasValue(1);

        assertThat(asyncJobService.requestCancellation(113L, jobId, user("owner"))).isTrue();
        assertThat(submissions).hasValue(1);
        submittedCleanup.get().run();
        assertThat(hookRuns).hasValue(1);
    }

    @Test
    void requestCancellation_terminalizesTheUiButRetainsTheSlotUntilTheWorkerDrains() {
        long exerciseId = 112L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        assertThat(jobService.recordEvent(exerciseId, jobId, progress("working"), false)).isTrue();
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/Before.java"));

        assertThat(jobService.requestCancellation(exerciseId, jobId, owner)).isTrue();

        ExerciseGenerationStatusDTO cancelled = jobService.getStatus(owner, exercise).orElseThrow();
        assertThat(cancelled.running()).isFalse();
        assertThat(cancelled.cancellable()).isFalse();
        assertThat(cancelled.events()).extracting(ExerciseGenerationEventDTO::type).containsExactly(ExerciseGenerationEventDTO.Type.PROGRESS,
                ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(cancelled.events().getLast().message()).isEqualTo("Generation was cancelled. Nothing was changed.");
        assertThat(cancelled.events().getLast().terminationReason()).isEqualTo(ExerciseGenerationEventDTO.TerminationReason.CANCELLED);
        assertThat(cancelled.fileChanges()).extracting(ExerciseGenerationFileChangeDTO::path).containsExactly("solution/Before.java");
        assertThat(jobService.hasActiveJob(exerciseId)).isTrue();
        assertThat(jobService.isCancelled(jobId)).isTrue();
        assertThat(jobService.requestCancellation(exerciseId, jobId, owner)).isTrue();

        assertThat(
                jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.done("late success", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true), true))
                .isFalse();
        assertThat(jobService.recordFileChange(exerciseId, jobId, fileChange("solution/Late.java"))).isFalse();

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
        GenerationJobService invalid = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofMinutes(1));

        assertThatThrownBy(invalid::init).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stale-job-timeout");
    }

    @Test
    void initRejectsAnEffortProfileDeadlineThatOutlastsTheStaleJobTimeout() {
        // The deployment default (45m) fits inside the 50m stale timeout, but the run a profile hands out lasts 55m and its slot would be reclaimed before it can finish.
        GenerationJobService raisedByProfile = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), null, Duration.ofMinutes(50), Duration.ofMinutes(45), Runnable::run, 1, Duration.ofHours(4), true, Duration.ofMinutes(55));

        assertThatThrownBy(raisedByProfile::init).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stale-job-timeout").hasMessageContaining("profiles");

        GenerationJobService withinTheTimeout = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), null, Duration.ofMinutes(50), Duration.ofMinutes(45), Runnable::run, 1, Duration.ofHours(4), true, Duration.ofMinutes(48));

        assertThatCode(withinTheTimeout::init).doesNotThrowAnyException();
    }

    @Test
    void requestCancellation_publishesCancelToOtherServiceInstances() {
        GenerationJobService ownerNode = jobService;
        GenerationJobService apiNode = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
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
        assertThat(jobService.requestCancellation(12L, jobId, user("notOwner"))).isFalse();
        assertThat(jobService.requestCancellation(12L, "different-job", user("owner"))).isFalse();
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

        // Cancellation already won this job under the job-map lock, so the caller must not be allowed to persist.
        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isFalse();
        assertThat(jobService.isCancelled(jobId)).isTrue();
        ExerciseGenerationStatusDTO status = jobService.getStatus(user("owner"), exercise(exerciseId)).orElseThrow();
        assertThat(status.events()).extracting(ExerciseGenerationEventDTO::type).containsExactly(ExerciseGenerationEventDTO.Type.CANCELLED);
    }

    @Test
    void requestCancellation_afterEnteringNonCancellablePhase_refusesAndDoesNotOverwriteTheSaveObligation() {
        long exerciseId = 141L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);

        // Persistence already won this job under the job-map lock, so a later cancel must not retroactively claim "nothing was changed".
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
        de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo> jobMap = (de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils
                .getField(jobService, "jobMap");
        Instant before = jobMap.get(String.valueOf(exerciseId)).lastHeartbeatOrStartedAt();

        assertThat(jobService.heartbeat(exerciseId, jobId)).isTrue();

        assertThat(jobMap.get(String.valueOf(exerciseId)).lastHeartbeatOrStartedAt()).isAfter(before);
    }

    @Test
    void heartbeat_refreshesTheInFlightBudgetReservation() {
        long exerciseId = 224L;
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        when(budgetService.refreshReservation("reservation-224")).thenReturn(true);
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(35), Duration.ofMinutes(30));
        service.init();
        String jobId = service.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE, "reservation-224");

        assertThat(service.heartbeat(exerciseId, jobId)).isTrue();

        verify(budgetService).refreshReservation("reservation-224");
    }

    @Test
    void heartbeat_failsClosedWhenTheBudgetReservationIsMissing() {
        long exerciseId = 225L;
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        when(budgetService.refreshReservation("reservation-225")).thenReturn(false);
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(35), Duration.ofMinutes(30));
        service.init();
        String jobId = service.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE, "reservation-225");
        @SuppressWarnings("unchecked")
        de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo> jobMap = (de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils
                .getField(service, "jobMap");
        Instant heartbeatBeforeAttempt = jobMap.get(String.valueOf(exerciseId)).lastHeartbeatOrStartedAt();

        assertThat(service.heartbeat(exerciseId, jobId)).isFalse();

        // The budget-reservation guard must short-circuit before the jobMap write, not merely report failure while still refreshing liveness.
        assertThat(jobMap.get(String.valueOf(exerciseId)).lastHeartbeatOrStartedAt()).isEqualTo(heartbeatBeforeAttempt);
    }

    @Test
    void heartbeat_afterAJobSpendsItsWholeTokenAllowance_stillReportsThisNodeAsTheOwner() {
        // Regression: exhaustion deleting the reservation made the heartbeat read a spent run as one that had lost ownership. The real budget service is wired in because the
        // defect lives in the interaction between the two, which a mock cannot show.
        long exerciseId = 226L;
        HyperionGenerationBudgetService budgetService = new HyperionGenerationBudgetService(mock(LLMTokenUsageTraceTestRepository.class),
                new HazelcastDistributedDataProviderService(hazelcastInstance), Duration.ofHours(24), 300, 0, 0, 300, Duration.ofMinutes(30));
        budgetService.init();
        HyperionGenerationBudgetService.BudgetReservation reservation = budgetService.reserveGenerationBudget(1L, 2L, 300);
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(35), Duration.ofMinutes(30));
        service.init();
        String jobId = service.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE, reservation.id());

        budgetService.recordPersistedUsage(reservation.id(), 300);

        assertThat(service.heartbeat(exerciseId, jobId)).isTrue();
    }

    @Test
    void clearStaleJobs_marksTranscriptDoneAndReleasesSlot() {
        long exerciseId = 125L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        @SuppressWarnings("unchecked")
        de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo> jobMap = (de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils
                .getField(jobService, "jobMap");
        jobMap.put(String.valueOf(exerciseId), new GenerationJobService.JobInfo(jobId, "owner", exerciseId, Instant.now().minus(Duration.ofMinutes(10)), null, "departed-node",
                Instant.now().minus(Duration.ofMinutes(10)), true, null));
        GenerationJobService shortTimeoutService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
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
    void clearStaleJobs_ownerDepartedWithFreshHeartbeat_marksTranscriptDoneAndReleasesSlot() {
        long exerciseId = 226L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        forceJobOwner(exerciseId, "departed-node");

        jobService.clearStaleJobs();

        assertThat(jobService.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isFalse();
            assertThat(status.events().getLast().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        });
        assertThat(jobService.hasActiveJob(exerciseId)).isFalse();
        assertThat(jobService.startJob(owner, exercise, "retry", GenerationMode.GENERATE)).isNotBlank();
    }

    @Test
    void staleHeartbeatWithLiveOwner_cancelsButRetainsTheSlotUntilTheWorkerDrains() {
        long exerciseId = 225L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE, "reservation-225");
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        GenerationJobService scannerNode = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(1), Duration.ofSeconds(30));
        scannerNode.init();

        scannerNode.clearStaleJobs();

        assertThat(scannerNode.isCancelled(jobId)).isTrue();
        assertThat(scannerNode.hasActiveJob(exerciseId)).isTrue();
        assertThat(scannerNode.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isFalse();
            assertThat(status.events().getLast().message()).contains("heartbeats");
        });
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> scannerNode.startJob(owner, exercise, "new", GenerationMode.GENERATE));
    }

    @Test
    void clearStaleJobs_broadcastsCancellationAndRejectsLateReplayWrites() {
        long exerciseId = 126L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/Before.java"));
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        GenerationJobService scannerNode = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofSeconds(30));
        scannerNode.init();

        scannerNode.clearStaleJobs();
        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.done("late success", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true), true);
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/After.java"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(hookRuns).hasValue(1));
        ExerciseGenerationStatusDTO status = jobService.getStatus(owner, exercise).orElseThrow();
        assertThat(status.running()).isFalse();
        assertThat(status.events().getLast().message()).contains("heartbeats");
        assertThat(status.fileChanges()).singleElement().satisfies(fileChange -> {
            assertThat(fileChange.path()).isEqualTo("solution/Before.java");
        });
    }

    @Test
    void clearStaleJobs_retainsBudgetReservationForAbandonedJob() {
        long exerciseId = 127L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE, "reservation-127");
        jobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        GenerationJobService scannerNode = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(1), Duration.ofSeconds(30));
        scannerNode.init();

        scannerNode.clearStaleJobs();

        verify(budgetService).retainReservationForBudgetWindow("reservation-127");
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
        GenerationJobService shortTimeoutService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
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
        GenerationJobService shortDeadlineService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
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
    void staleHeartbeat_retainsNonCancellablePersistenceSlotAfterOwnerLeavesCluster() {
        long exerciseId = 228L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "old", GenerationMode.GENERATE, "reservation-228");
        assertThat(jobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
        forceJobHeartbeat(exerciseId, jobId, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        HyperionGenerationBudgetService budgetService = mock(HyperionGenerationBudgetService.class);
        GenerationJobService shortTimeoutService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(1), Duration.ofSeconds(30));
        shortTimeoutService.init();

        shortTimeoutService.clearStaleJobs();

        // Membership loss is a failure-detector result, not proof the departed owner stopped writing, so the non-cancellable slot is retained against an un-fenced writer.
        assertThat(shortTimeoutService.hasActiveJob(exerciseId)).isTrue();
        assertThat(shortTimeoutService.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isTrue();
            assertThat(status.jobId()).isEqualTo(jobId);
            assertThat(status.cancellable()).isFalse();
        });
        verify(budgetService, never()).retainReservationForBudgetWindow("reservation-228");
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> shortTimeoutService.startJob(owner, exercise, "new", GenerationMode.GENERATE));
    }

    @Test
    void staleHeartbeat_retainsNonCancellableRevertSlotAfterOwnerLeavesCluster() {
        long exerciseId = 229L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String token = jobService.claimRevertSlot(owner, exerciseId);
        forceJobHeartbeat(exerciseId, token, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        GenerationJobService shortTimeoutService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofSeconds(30));
        shortTimeoutService.init();

        shortTimeoutService.clearStaleJobs();

        // Same fail-closed rule as the persistence barrier: a departed owner mid force-reset may still be writing to the Git server.
        assertThat(shortTimeoutService.hasActiveJob(exerciseId)).isTrue();
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> shortTimeoutService.startJob(owner, exercise, "new", GenerationMode.GENERATE));
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> shortTimeoutService.claimRevertSlot(owner, exerciseId));
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

    @Test
    void recordSpecDocument_isReturnedInStatus() {
        long exerciseId = 230L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);

        assertThat(jobService.recordSpecDocument(exerciseId, jobId, "## Rules\n- R1: computes a result")).isTrue();

        assertThat(jobService.getStatus(owner, exercise).orElseThrow().specDocument()).isEqualTo("## Rules\n- R1: computes a result");
    }

    @Test
    void getStatus_omitsSpecDocumentWhenNeverRecorded() {
        long exerciseId = 231L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);

        assertThat(jobService.getStatus(owner, exercise).orElseThrow().specDocument()).isNull();
    }

    @Test
    void recordSpecDocument_beyondCap_truncatesWithMarker() {
        long exerciseId = 232L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        String oversized = "x".repeat(30_000);

        assertThat(jobService.recordSpecDocument(exerciseId, jobId, oversized)).isTrue();

        String retained = jobService.getStatus(owner, exercise).orElseThrow().specDocument();
        assertThat(retained).hasSizeLessThan(oversized.length());
        assertThat(retained).startsWith("x".repeat(100)).contains("truncated");
    }

    @Test
    void recordSpecDocument_forAStaleJobId_isIgnored() {
        long exerciseId = 233L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);

        assertThat(jobService.recordSpecDocument(exerciseId, "different-job", "## Rules")).isFalse();
        assertThat(jobService.getStatus(owner, exercise).orElseThrow().specDocument()).isNull();
    }

    @Test
    void recordSpecDocument_retainedThroughToTheTerminalReplay() {
        long exerciseId = 234L;
        ProgrammingExercise exercise = exercise(exerciseId);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);
        assertThat(jobService.recordSpecDocument(exerciseId, jobId, "## Rules\n- R1: computes a result")).isTrue();

        jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.done("done", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, false), true);
        jobService.clearJob(exerciseId, jobId);

        assertThat(jobService.getStatus(owner, exercise).orElseThrow().specDocument()).isEqualTo("## Rules\n- R1: computes a result");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void tokenUsageSink_stopsTheRunExactlyWhenAModelCallCouldNotBeAccounted(boolean recorded) {
        // A run whose token spend cannot be attributed must stop rather than keep calling the provider off the books.
        LLMTokenUsageService tokenUsageService = mock(LLMTokenUsageService.class);
        when(tokenUsageService.trackChatResponseTokenUsage(any(), any(), anyString(), any(), any())).thenReturn(recorded);
        GenerationJobService accountingService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
        }, tokenUsageService);
        accountingService.init();
        Consumer<ChatResponse> sink = accountingService.tokenUsageSink(3L, 4L, 5L);
        ThrowingCallable recordOneResponse = () -> sink.accept(mock(ChatResponse.class));

        if (recorded) {
            assertThatCode(recordOneResponse).doesNotThrowAnyException();
        }
        else {
            assertThatExceptionOfType(GenerationJobService.TokenUsageAccountingException.class).isThrownBy(recordOneResponse);
        }
    }

    @Test
    void isOwnedActiveJob_isTrueOnlyForThisNodesLiveJob() {
        // The stale-writer guard consulted before every durable Git/DB write.
        long exerciseId = 235L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE);

        assertThat(jobService.isOwnedActiveJob(exerciseId, jobId)).as("this node's live job").isTrue();
        assertThat(jobService.isOwnedActiveJob(exerciseId, "a-previous-job")).as("a superseded job id").isFalse();
        assertThat(jobService.isOwnedActiveJob(exerciseId + 1, jobId)).as("an exercise with no job at all").isFalse();

        forceJobOwner(exerciseId, "departed-node");

        assertThat(jobService.isOwnedActiveJob(exerciseId, jobId)).as("the slot was taken over by another node").isFalse();
    }

    private void forceJobHeartbeat(long exerciseId, String jobId, Instant heartbeatAt) {
        @SuppressWarnings("unchecked")
        de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo> jobMap = (de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils
                .getField(jobService, "jobMap");
        GenerationJobService.JobInfo existing = jobMap.get(String.valueOf(exerciseId));
        String localNodeId = (String) ReflectionTestUtils.getField(jobService, "localNodeId");
        jobMap.put(String.valueOf(exerciseId), new GenerationJobService.JobInfo(jobId, existing.userLogin(), exerciseId, existing.startedAt(), existing.deadlineAt(), localNodeId,
                heartbeatAt, existing.cancellable(), existing.budgetReservationId()));
    }

    private void forceJobOwner(long exerciseId, String ownerNodeId) {
        @SuppressWarnings("unchecked")
        de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo> jobMap = (de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils
                .getField(jobService, "jobMap");
        GenerationJobService.JobInfo existing = jobMap.get(String.valueOf(exerciseId));
        jobMap.put(String.valueOf(exerciseId), new GenerationJobService.JobInfo(existing.jobId(), existing.userLogin(), exerciseId, existing.startedAt(), existing.deadlineAt(),
                ownerNodeId, existing.lastHeartbeatAt(), existing.cancellable(), existing.budgetReservationId()));
    }

    private static void forceJobDeadline(GenerationJobService service, long exerciseId, Instant deadlineAt) {
        @SuppressWarnings("unchecked")
        de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo> jobMap = (de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils
                .getField(service, "jobMap");
        GenerationJobService.JobInfo existing = jobMap.get(String.valueOf(exerciseId));
        jobMap.put(String.valueOf(exerciseId), new GenerationJobService.JobInfo(existing.jobId(), existing.userLogin(), exerciseId, existing.startedAt(), deadlineAt,
                existing.ownerNodeId(), existing.lastHeartbeatAt(), existing.cancellable(), existing.budgetReservationId()));
    }

    private static ExerciseGenerationFileChangeDTO fileChange(String path) {
        return ExerciseGenerationFileChangeDTO.of(path, ExerciseGenerationFileChangeDTO.ACTION_WRITE, 1);
    }

    private IMap<String, GenerationJobService.JobInfo> jobMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-jobs");
    }

    @Test
    void correctnessCriticalJobSlotLockDoesNotExpire() {
        @SuppressWarnings("unchecked")
        de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo> original = (de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils
                .getField(jobService, "jobMap");
        var observed = spy(original);
        ReflectionTestUtils.setField(jobService, "jobMap", observed);

        jobService.startJob(user("owner"), exercise(454L), "generate", GenerationMode.GENERATE);

        verify(observed).lock("454");
        verify(observed, never()).lock(eq("454"), any(Duration.class));
    }

    private CountDownLatch captureNextJobLockAttempt(String key) {
        return captureNextJobLockAttempt(jobService, key);
    }

    private CountDownLatch captureNextJobLockAttempt(GenerationJobService service, String key) {
        IMap<String, GenerationJobService.JobInfo> jobMap = jobMap();
        IMap<String, GenerationJobService.JobInfo> observedJobMap = spy(jobMap);
        CountDownLatch lockAttempted = new CountDownLatch(1);
        doAnswer(invocation -> {
            lockAttempted.countDown();
            jobMap.lock(key);
            return null;
        }).when(observedJobMap).lock(key);
        ReflectionTestUtils.setField(service, "jobMap", new de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedMap<>(observedJobMap));
        GenerationJobReplayStore replayStore = (GenerationJobReplayStore) ReflectionTestUtils.getField(service, "replayStore");
        ReflectionTestUtils.setField(replayStore, "jobMap", new de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedMap<>(observedJobMap));
        return lockAttempted;
    }

    private IMap<String, GenerationJobService.JobTranscript> transcriptMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-transcripts");
    }

    private IMap<String, GenerationJobService.JobFileChangeIndex> fileChangeMap() {
        return hazelcastInstance.getMap(GenerationJobReplayStore.FILE_CHANGE_MAP_NAME);
    }

    @Test
    void recordFileChangeKeepsLatestChangePerPathAndBoundsReplay() {
        long exerciseId = 501L;
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);

        for (int i = 0; i < GenerationJobReplayStore.MAX_RETAINED_FILE_CHANGES + 1; i++) {
            jobService.recordFileChange(exerciseId, jobId, fileChange("solution/File" + i + ".java"));
        }
        jobService.recordFileChange(exerciseId, jobId, ExerciseGenerationFileChangeDTO.of("solution/File1.java", ExerciseGenerationFileChangeDTO.ACTION_EDIT, 9));

        List<ExerciseGenerationFileChangeDTO> replay = jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileChanges();
        assertThat(replay).hasSize(GenerationJobReplayStore.MAX_RETAINED_FILE_CHANGES);
        assertThat(replay.getFirst().path()).isEqualTo("solution/File1.java");
        assertThat(replay.getFirst().action()).isEqualTo(ExerciseGenerationFileChangeDTO.ACTION_EDIT);
        assertThat(replay.getFirst().turn()).isEqualTo(9);
        assertThat(replay.getLast().path()).isEqualTo("solution/File" + GenerationJobReplayStore.MAX_RETAINED_FILE_CHANGES + ".java");
    }

    @Test
    void recordFileChangeDropsStaleJobAndNewRunStartsClean() {
        long exerciseId = 502L;
        User owner = user("owner");
        String firstJob = jobService.startJob(owner, exercise(exerciseId), "first", GenerationMode.GENERATE);
        jobService.recordFileChange(exerciseId, firstJob, fileChange("solution/Old.java"));
        jobService.clearJob(exerciseId, firstJob);

        String secondJob = jobService.startJob(owner, exercise(exerciseId), "second", GenerationMode.GENERATE);
        assertThat(jobService.recordFileChange(exerciseId, firstJob, fileChange("solution/Stale.java"))).isFalse();

        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileChanges()).isEmpty();
        assertThat(jobService.recordFileChange(exerciseId, secondJob, fileChange("solution/New.java"))).isTrue();
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileChanges()).extracting(ExerciseGenerationFileChangeDTO::path)
                .containsExactly("solution/New.java");
    }

    @Test
    void startJob_whenExecutorRejectsPublish_releasesSlotAndReportsBusy_notWedged() {
        AtomicBoolean reject = new AtomicBoolean(true);
        AtomicInteger publishAttempts = new AtomicInteger(0);
        ApplicationEventPublisher rejectingPublisher = event -> {
            if (event instanceof GenerationStartedEvent) {
                publishAttempts.incrementAndGet();
            }
            if (reject.get() && event instanceof GenerationStartedEvent) {
                throw new TaskRejectedException("hyperionGenerationExecutor is saturated");
            }
        };
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), rejectingPublisher,
                mock(LLMTokenUsageService.class));
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
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), publisher, mock(LLMTokenUsageService.class));
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
        jobService.recordFileChange(exerciseId, firstJob, fileChange("solution/Before.java"));
        jobService.recordEvent(exerciseId, firstJob, ExerciseGenerationEventDTO.done("done", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true), true);
        jobService.clearJob(exerciseId, firstJob);
        assertThat(jobService.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo(firstJob);
            assertThat(status.fileChanges()).singleElement().extracting(ExerciseGenerationFileChangeDTO::path).isEqualTo("solution/Before.java");
        });
        GenerationJobService failingService = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
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
    void startJob_whenTranscriptInitializationFails_releasesSlotAndAllowsRetry() {
        long exerciseId = 82L;
        String key = String.valueOf(exerciseId);
        GenerationJobReplayStore replayStore = (GenerationJobReplayStore) ReflectionTestUtils.getField(jobService, "replayStore");
        IMap<String, GenerationJobService.JobTranscript> originalTranscriptMap = transcriptMap();
        IMap<String, GenerationJobService.JobTranscript> failingTranscriptMap = spy(originalTranscriptMap);
        doThrow(new IllegalStateException("transcript initialization failed")).when(failingTranscriptMap).put(eq(key), any(GenerationJobService.JobTranscript.class));
        ReflectionTestUtils.setField(replayStore, "transcriptMap", new de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedMap<>(failingTranscriptMap));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE))
                .withMessageContaining("transcript initialization failed");

        ReflectionTestUtils.setField(replayStore, "transcriptMap", new de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedMap<>(originalTranscriptMap));
        assertThat(jobService.hasActiveJob(exerciseId)).isFalse();
        assertThat(jobService.startJob(user("owner"), exercise(exerciseId), "retry", GenerationMode.GENERATE)).isNotBlank();
    }

    @Test
    void startJob_whenFileChangeInitializationFails_releasesSlotAndAllowsRetry() {
        long exerciseId = 83L;
        String key = String.valueOf(exerciseId);
        GenerationJobReplayStore replayStore = (GenerationJobReplayStore) ReflectionTestUtils.getField(jobService, "replayStore");
        IMap<String, GenerationJobService.JobFileChangeIndex> originalFileChangeMap = fileChangeMap();
        IMap<String, GenerationJobService.JobFileChangeIndex> failingFileChangeMap = spy(originalFileChangeMap);
        doThrow(new IllegalStateException("file-change initialization failed")).when(failingFileChangeMap).put(eq(key), any(GenerationJobService.JobFileChangeIndex.class));
        ReflectionTestUtils.setField(replayStore, "fileChangeMap", new de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedMap<>(failingFileChangeMap));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE))
                .withMessageContaining("file-change initialization failed");

        ReflectionTestUtils.setField(replayStore, "fileChangeMap", new de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedMap<>(originalFileChangeMap));
        assertThat(jobService.hasActiveJob(exerciseId)).isFalse();
        assertThat(jobService.startJob(user("owner"), exercise(exerciseId), "retry", GenerationMode.GENERATE)).isNotBlank();
    }

    @Test
    void startJob_withANarrowedRunDeadline_recordsThatDeadlineRatherThanTheDeploymentWide() {
        // Admission's recorded deadline and the deadline the worker enforces must be the same one, or a narrowed run gets reported as stopped by a limit it was never given.
        List<GenerationStartedEvent> published = new ArrayList<>();
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(hazelcastInstance), event -> {
            if (event instanceof GenerationStartedEvent started) {
                published.add(started);
            }
        }, mock(LLMTokenUsageService.class));
        service.init();
        HyperionGenerationSettings narrowed = new HyperionGenerationSettings("draft", "Quick draft", 20, Duration.ofMinutes(12), 600_000L, true, "CONTINUOUS", 128_000, null, false,
                false);
        Instant before = Instant.now();

        service.startJob(user("owner"), exercise(9_001L), "generate", GenerationMode.GENERATE, null, null, narrowed);

        assertThat(published).hasSize(1);
        assertThat(published.getFirst().settings()).isEqualTo(narrowed);
        // The deployment default is 30 minutes in this test constructor, so a 12-minute profile must land well below it.
        assertThat(published.getFirst().deadlineAt()).isBetween(before.plus(Duration.ofMinutes(12)), before.plus(Duration.ofMinutes(13)));
    }

    @Test
    void getStatus_echoesTheEffortProfileTheRunResolvedTo() {
        // The caller must be able to verify what ran, not what it asked for.
        ProgrammingExercise exercise = exercise(9_002L);
        User owner = user("owner");
        HyperionGenerationSettings thorough = new HyperionGenerationSettings("thorough", "Thorough", 90, Duration.ofMinutes(20), 6_000_000L, true, "CONTINUOUS", 128_000, null,
                false, false);

        jobService.startJob(owner, exercise, "generate", GenerationMode.GENERATE, null, null, thorough);

        assertThat(jobService.getStatus(owner, exercise)).get().extracting(ExerciseGenerationStatusDTO::effortProfile).isEqualTo("thorough");
    }

    @Test
    void getStatus_withoutConfiguredProfiles_omitsTheEffortProfile() {
        ProgrammingExercise exercise = exercise(9_003L);
        User owner = user("owner");

        jobService.startJob(owner, exercise, "generate", GenerationMode.GENERATE);

        assertThat(jobService.getStatus(owner, exercise)).get().extracting(ExerciseGenerationStatusDTO::effortProfile).isNull();
    }
}
