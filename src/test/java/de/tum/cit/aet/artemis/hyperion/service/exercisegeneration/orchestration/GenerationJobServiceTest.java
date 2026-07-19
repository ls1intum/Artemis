package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;
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
    void claimExternalMutationSlot_doesNotExpireWhileTheCallerStillHoldsTheLease() {
        long exerciseId = 443L;

        jobService.claimExternalMutationSlot(exerciseId);

        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(jobService, "jobMap");
        assertThat(jobMap.getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void activeGenerationSlot_doesNotExpireForSupportedLongRunningJobs() {
        long exerciseId = 447L;
        GenerationJobService longRunningJobService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofHours(3).plusMinutes(1), Duration.ofHours(3));
        longRunningJobService.init();

        String jobId = longRunningJobService.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(longRunningJobService, "jobMap");
        assertThat(jobMap.getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);

        assertThat(longRunningJobService.heartbeat(exerciseId, jobId)).isTrue();
        assertThat(jobMap.getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);

        assertThat(longRunningJobService.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
        assertThat(jobMap.getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);

        longRunningJobService.recordEvent(exerciseId, jobId, progress("still running"), false);
        longRunningJobService.recordFileChange(exerciseId, jobId, fileChange("solution/Preview.java", "changed"));
        assertThat(jobMap.getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);
        assertThat(transcriptMap().getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);
        assertThat(fileChangeMap().getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void activeCancellationMarker_doesNotExpireBeforeTheJobClears() {
        long exerciseId = 448L;
        String jobId = jobService.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);

        assertThat(jobService.requestCancellation(exerciseId, jobId, user("owner"))).isTrue();
        @SuppressWarnings("unchecked")
        IMap<String, Boolean> cancellationMap = (IMap<String, Boolean>) ReflectionTestUtils.getField(jobService, "cancellationMap");
        assertThat(cancellationMap.getEntryView(jobId).getTtl()).isEqualTo(Long.MAX_VALUE);
        assertThat(transcriptMap().getEntryView(String.valueOf(exerciseId)).getTtl()).isEqualTo(Long.MAX_VALUE);

        jobService.clearJob(exerciseId, jobId);
        assertThat(cancellationMap.get(jobId)).isNull();
        assertThat(transcriptMap().getEntryView(String.valueOf(exerciseId)).getTtl()).isBetween(1L, TimeUnit.MINUTES.toMillis(15));
    }

    @Test
    void claimSlot_whenDataMemberCountDiffersFromConfiguredTopology_failsClosed() {
        GenerationJobService mismatchedTopologyService = new GenerationJobService(hazelcastInstance, event -> {
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
        GenerationJobService service = new GenerationJobService(observedHazelcast, event -> {
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

    @Test
    void clearStaleJobs_keepsStaleExternalMutationWhileOwnerIsPresent() {
        long exerciseId = 444L;
        String token = jobService.claimExternalMutationSlot(exerciseId);
        forceJobHeartbeat(exerciseId, token, Instant.now().minus(Duration.ofMinutes(10)));
        GenerationJobService scanner = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofSeconds(30));
        scanner.init();

        scanner.clearStaleJobs();

        assertThat(scanner.hasActiveJob(exerciseId)).isTrue();
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> scanner.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE));
        jobService.clearExternalMutationSlot(exerciseId, token);
    }

    @Test
    void clearStaleJobs_keepsExternalMutationAfterOwnerLeavesClusterUntilValueGuardedRecovery() {
        long exerciseId = 445L;
        String token = jobService.claimExternalMutationSlot(exerciseId);
        forceJobHeartbeat(exerciseId, token, Instant.now().minus(Duration.ofMinutes(10)));
        forceJobOwner(exerciseId, "departed-node");
        GenerationJobService scanner = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofSeconds(30));
        scanner.init();

        scanner.clearStaleJobs();

        assertThat(scanner.hasActiveJob(exerciseId)).isTrue();
        assertThat(scanner.getExternalMutationInfo(exerciseId)).hasValueSatisfying(info -> {
            assertThat(info.token()).isEqualTo(token);
            assertThat(info.ownerNodeId()).isEqualTo("departed-node");
        });
        assertThat(scanner.recoverExternalMutationSlot(exerciseId, "external-mutation-wrong")).isFalse();
        assertThat(scanner.hasActiveJob(exerciseId)).isTrue();
        assertThat(scanner.recoverExternalMutationSlot(exerciseId, token)).isTrue();
        assertThat(scanner.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE)).isNotBlank();
    }

    @Test
    void recoverExternalMutationSlot_rejectsLiveOwner() {
        long exerciseId = 447L;
        String token = jobService.claimExternalMutationSlot(exerciseId);

        assertThat(jobService.recoverExternalMutationSlot(exerciseId, token)).isFalse();
        assertThat(jobService.hasActiveJob(exerciseId)).isTrue();

        jobService.clearExternalMutationSlot(exerciseId, token);
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
        GenerationJobService publishingService = new GenerationJobService(hazelcastInstance, publishedEvents::add, mock(LLMTokenUsageService.class));
        publishingService.init();
        ProgrammingExercise exercise = exercise(442L);

        String jobId = publishingService.startJob(user("owner"), exercise, "private prompt", GenerationMode.GENERATE);
        publishingService.clearJob(exercise.getId(), jobId);

        assertThat(publishedEvents).filteredOn(ExerciseGenerationStateChangedEvent.class::isInstance).containsExactly(
                new ExerciseGenerationStateChangedEvent(new ExerciseGenerationStateDTO(exercise.getId(), jobId, true)),
                new ExerciseGenerationStateChangedEvent(new ExerciseGenerationStateDTO(exercise.getId(), jobId, false)));
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
        GenerationJobService publishingService = new GenerationJobService(hazelcastInstance, publisher, mock(LLMTokenUsageService.class));
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
        jobService.recordFileChange(exerciseId, jobId, fileChange("solution/A.java", "adapted"));
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
        IMap<String, GenerationJobService.JobInfo> jobMap = jobMap();
        String key = String.valueOf(exercise.getId());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAttempted = captureNextJobLockAttempt(key);
        AtomicBoolean lockHeld = new AtomicBoolean(true);

        jobMap.lock(key);
        try {
            Future<Optional<ExerciseGenerationStatusDTO>> status = executor.submit(() -> jobService.getStatus(owner, exercise));
            assertThat(lockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(status.isDone()).isFalse();

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

        // Only the mid-run transcript (prompts/tool activity) is hidden from a non-owner authorized instructor; the terminal outcome itself carries the same exact review
        // identity (message, verdict, saved commits, saved version id) the owner sees, since none of it is sensitive to another instructor with editor access to this exercise.
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

        assertThat(jobService.requestCancellation(11L, jobId, user("owner"))).isTrue();
        assertThat(hookRuns.get()).isEqualTo(1);
    }

    @Test
    void requestCancellation_whenClusterInterruptPublicationFails_stillReturnsSuccess() {
        HazelcastInstance failingHazelcastInstance = spy(hazelcastInstance);
        @SuppressWarnings("unchecked")
        ITopic<Object> failingCancelTopic = mock(ITopic.class);
        doReturn(failingCancelTopic).when(failingHazelcastInstance).getTopic("hyperion-exercise-generation-cancel-requests");
        doThrow(new IllegalStateException("cancel topic unavailable")).when(failingCancelTopic).publish(any());
        GenerationJobService failingPublishJobService = new GenerationJobService(failingHazelcastInstance, event -> {
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
        GenerationJobService publishingJobService = new GenerationJobService(hazelcastInstance, publishedEvent::set, mock(LLMTokenUsageService.class));
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
        GenerationJobService asyncJobService = new GenerationJobService(hazelcastInstance, event -> {
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
        assertThat(jobService.requestCancellation(exerciseId, jobId, owner)).isTrue();

        assertThat(
                jobService.recordEvent(exerciseId, jobId, ExerciseGenerationEventDTO.done("late success", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true), true))
                .isFalse();
        assertThat(jobService.recordFileChange(exerciseId, jobId, fileChange("solution/Late.java", "late"))).isFalse();

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
        when(budgetService.refreshReservation("reservation-224")).thenReturn(true);
        GenerationJobService service = new GenerationJobService(hazelcastInstance, event -> {
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
        GenerationJobService service = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), budgetService, Duration.ofMinutes(35), Duration.ofMinutes(30));
        service.init();
        String jobId = service.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE, "reservation-225");
        @SuppressWarnings("unchecked")
        IMap<String, GenerationJobService.JobInfo> jobMap = (IMap<String, GenerationJobService.JobInfo>) ReflectionTestUtils.getField(service, "jobMap");
        Instant heartbeatBeforeAttempt = jobMap.get(String.valueOf(exerciseId)).lastHeartbeatOrStartedAt();

        assertThat(service.heartbeat(exerciseId, jobId)).isFalse();

        // The budget-reservation guard must short-circuit before the jobMap write, not merely report failure while still refreshing liveness.
        assertThat(jobMap.get(String.valueOf(exerciseId)).lastHeartbeatOrStartedAt()).isEqualTo(heartbeatBeforeAttempt);
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
    void clearStaleJobs_retainsBudgetReservationForAbandonedJob() {
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

        verify(budgetService).retainReservationForBudgetWindow("reservation-127");
        verify(budgetService, never()).releaseReservation("reservation-127");
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
    void staleHeartbeat_retainsNonCancellablePersistenceSlotAfterOwnerLeavesCluster() {
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

        // Cluster membership loss is a failure-detector result, not proof that the departed owner's Git/DB persistence request has actually stopped (GC pause, partition,
        // false-positive detection). The non-cancellable slot must be retained so a replacement generation cannot interleave with an un-fenced writer, even though the owner is
        // absent from the local membership view.
        assertThat(shortTimeoutService.hasActiveJob(exerciseId)).isTrue();
        assertThat(shortTimeoutService.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isTrue();
            assertThat(status.jobId()).isEqualTo(jobId);
            assertThat(status.cancellable()).isFalse();
        });
        verify(budgetService, never()).retainReservationForBudgetWindow("reservation-228");
        verify(budgetService, never()).releaseReservation("reservation-228");
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
        GenerationJobService shortTimeoutService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), Duration.ofMinutes(1), Duration.ofSeconds(30));
        shortTimeoutService.init();

        shortTimeoutService.clearStaleJobs();

        // Same fail-closed rule as the persistence barrier above: a departed owner mid force-reset may still be writing to the Git server, so the revert barrier must not be
        // freed just because the owner left the cluster view. Both a new generation and a new revert must conflict with the retained slot.
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
        ReflectionTestUtils.setField(service, "jobMap", observedJobMap);
        GenerationJobReplayStore replayStore = (GenerationJobReplayStore) ReflectionTestUtils.getField(service, "replayStore");
        ReflectionTestUtils.setField(replayStore, "jobMap", observedJobMap);
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
            jobService.recordFileChange(exerciseId, jobId, fileChange("solution/File" + i + ".java", "body"));
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
        jobService.recordFileChange(exerciseId, firstJob, fileChange("solution/Old.java", "old"));
        jobService.clearJob(exerciseId, firstJob);

        String secondJob = jobService.startJob(owner, exercise(exerciseId), "second", GenerationMode.GENERATE);
        assertThat(jobService.recordFileChange(exerciseId, firstJob, fileChange("solution/Stale.java", "stale"))).isFalse();

        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileChanges()).isEmpty();
        assertThat(jobService.recordFileChange(exerciseId, secondJob, fileChange("solution/New.java", "new"))).isTrue();
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
    void startJob_whenTranscriptInitializationFails_releasesSlotAndAllowsRetry() {
        long exerciseId = 82L;
        String key = String.valueOf(exerciseId);
        GenerationJobReplayStore replayStore = (GenerationJobReplayStore) ReflectionTestUtils.getField(jobService, "replayStore");
        IMap<String, GenerationJobService.JobTranscript> originalTranscriptMap = transcriptMap();
        IMap<String, GenerationJobService.JobTranscript> failingTranscriptMap = spy(originalTranscriptMap);
        doThrow(new IllegalStateException("transcript initialization failed")).when(failingTranscriptMap).set(eq(key), any(GenerationJobService.JobTranscript.class));
        ReflectionTestUtils.setField(replayStore, "transcriptMap", failingTranscriptMap);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE))
                .withMessageContaining("transcript initialization failed");

        ReflectionTestUtils.setField(replayStore, "transcriptMap", originalTranscriptMap);
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
        doThrow(new IllegalStateException("file-change initialization failed")).when(failingFileChangeMap).set(eq(key), any(GenerationJobService.JobFileChangeIndex.class));
        ReflectionTestUtils.setField(replayStore, "fileChangeMap", failingFileChangeMap);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> jobService.startJob(user("owner"), exercise(exerciseId), "go", GenerationMode.GENERATE))
                .withMessageContaining("file-change initialization failed");

        ReflectionTestUtils.setField(replayStore, "fileChangeMap", originalFileChangeMap);
        assertThat(jobService.hasActiveJob(exerciseId)).isFalse();
        assertThat(jobService.startJob(user("owner"), exercise(exerciseId), "retry", GenerationMode.GENERATE)).isNotBlank();
    }

}
