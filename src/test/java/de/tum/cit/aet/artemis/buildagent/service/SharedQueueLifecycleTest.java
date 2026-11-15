package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildRunnerType;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.localci.exception.ImagePullException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

class SharedQueueLifecycleTest {

    private final BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);

    private final BuildJobManagementService management = mock(BuildJobManagementService.class);

    private final BuildLogsMap logs = mock(BuildLogsMap.class);

    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    private final BuildJobRunner runner = mock(BuildJobRunner.class);

    private final BuildAgentInformationService information = mock(BuildAgentInformationService.class);

    private final DistributedDataAccessService distributed = mock(DistributedDataAccessService.class);

    private final LocalDataProviderService provider = new LocalDataProviderService();

    @SuppressWarnings("unchecked")
    private final DistributedQueue<BuildJobQueueItem> jobs = mock(DistributedQueue.class);

    private final java.util.ArrayDeque<BuildJobQueueItem> queuedJobs = new java.util.ArrayDeque<>();

    private final DistributedQueue<ResultQueueItem> results = provider.getQueue("results");

    private final DistributedMap<String, BuildJobQueueItem> processing = provider.getMap("processing");

    private final DistributedTopic<String> pause = provider.getTopic("pause");

    private final DistributedTopic<String> resume = provider.getTopic("resume");

    private final Map<String, BuildAgentInformation> agents = new HashMap<>();

    private final ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);

    private final List<Runnable> scheduledTasks = new ArrayList<>();

    private final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();

    private final AtomicReference<Consumer<Boolean>> connectionListener = new AtomicReference<>();

    private final SharedQueueProcessingService service = new SharedQueueProcessingService(configuration, management, logs, scheduler, runner, information, distributed);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "buildAgentShortName", "agent-one");
        ReflectionTestUtils.setField(service, "buildAgentDisplayName", "");
        when(distributed.isConnectedToCluster()).thenReturn(true);
        when(distributed.isInstanceRunning()).thenReturn(true);
        when(distributed.getLocalMemberAddress()).thenReturn("[127.0.0.1]:5701");
        when(distributed.getDistributedBuildJobQueue()).thenReturn(jobs);
        when(jobs.add(any())).thenAnswer(call -> queuedJobs.add(call.getArgument(0)));
        when(jobs.poll()).thenAnswer(_ -> queuedJobs.poll());
        when(jobs.isEmpty()).thenAnswer(_ -> queuedJobs.isEmpty());
        when(jobs.size()).thenAnswer(_ -> queuedJobs.size());
        when(jobs.addItemListener(any())).thenAnswer(_ -> UUID.randomUUID());
        when(distributed.getDistributedBuildResultQueue()).thenReturn(results);
        when(distributed.getDistributedProcessingJobs()).thenReturn(processing);
        when(distributed.getPauseBuildAgentTopic()).thenReturn(pause);
        when(distributed.getResumeBuildAgentTopic()).thenReturn(resume);
        when(distributed.getBuildAgentInformationMap()).thenReturn(agents);
        when(distributed.getClusterMemberAddresses()).thenReturn(Set.of("[127.0.0.1]:5701"));
        when(configuration.getBuildExecutor()).thenReturn(executor);
        when(executor.getMaximumPoolSize()).thenReturn(1);
        when(executor.getQueue()).thenReturn(new LinkedBlockingQueue<>());
        when(configuration.getPauseAfterConsecutiveFailedJobs()).thenReturn(100);
        when(management.getRunningBuildJobIds()).thenReturn(Set.of());
        when(runner.type()).thenReturn(BuildRunnerType.DOCKER);
        when(logs.getAndTruncateBuildLogs(any())).thenReturn(List.of());
        doAnswer(call -> {
            Consumer<Boolean> listener = call.getArgument(0);
            connectionListener.set(listener);
            if (distributed.isConnectedToCluster()) {
                listener.accept(true);
            }
            return UUID.randomUUID();
        }).when(distributed).addConnectionStateListener(any());
        doAnswer(call -> {
            scheduledTasks.add(call.getArgument(0));
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            scheduledFutures.add(future);
            return future;
        }).when(scheduler).scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
    }

    @AfterEach
    void clearSecurityContext() {
        service.removeListenerAndCancelScheduledFuture();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "Agent", "agent_one", "agent one", "agent/one" })
    void rejectsInvalidAgentNamesBeforeRegisteringListeners(String name) {
        ReflectionTestUtils.setField(service, "buildAgentShortName", name);
        assertThatThrownBy(service::init).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lowercase letters, numbers and hyphens");
        verify(distributed, never()).addConnectionStateListener(any());
    }

    @Test
    void delayedConnectionRetriesAndReconnectionReplaceSchedulesAndListeners() {
        when(distributed.isConnectedToCluster()).thenReturn(false);
        service.init();
        assertThat(scheduledTasks).hasSize(1);
        scheduledTasks.getFirst().run();
        assertThat(scheduledTasks).hasSize(1);
        when(distributed.isConnectedToCluster()).thenReturn(true);
        scheduledTasks.getFirst().run();
        assertThat(scheduledTasks).hasSize(2);
        verify(scheduledFutures.getFirst()).cancel(false);
        connectionListener.get().accept(true);
        assertThat(scheduledTasks).hasSize(2);
        connectionListener.get().accept(false);
        assertThat(scheduledTasks).hasSize(3);
        verify(scheduledFutures.get(1), times(2)).cancel(false);
        pause.publish("other-agent");
        assertThat(service.isPaused()).isFalse();
        pause.publish("agent-one");
        assertThat(service.isPaused()).isTrue();
        verify(configuration).closeBuildAgentServices();
        pause.publish("agent-one");
        verify(configuration, times(1)).closeBuildAgentServices();
    }

    @Test
    void successPublishesCompletedResultClearsCloneTokenAndReleasesAttempt() {
        CompletableFuture<BuildResult> future = new CompletableFuture<>();
        when(management.executeBuildJob(any())).thenReturn(future);
        List<BuildLogDTO> buildLogs = List.of(new BuildLogDTO(ZonedDateTime.now(), "build output"));
        when(logs.getAndTruncateBuildLogs("job")).thenReturn(buildLogs);
        service.init();
        ZonedDateTime beforeClaim = ZonedDateTime.now();
        submit(buildJob(0));
        ZonedDateTime afterClaim = ZonedDateTime.now();
        BuildJobQueueItem claimed = processing.get("job");
        assertThat(claimed.buildAgent().name()).isEqualTo("agent-one");
        assertThat(claimed.buildAgent().displayName()).isEqualTo("agent-one");
        assertThat(claimed.jobTimingInfo().estimatedCompletionDate()).isBetween(beforeClaim, afterClaim);
        BuildResult result = new BuildResult("main", "assignment", "tests", buildLogs, true);
        future.complete(result);
        assertThat(processing.get("job")).isNull();
        ResultQueueItem published = results.poll();
        assertThat(published.buildResult()).isSameAs(result);
        assertThat(published.buildLogs()).isEqualTo(buildLogs);
        assertThat(published.buildJobQueueItem().status()).isEqualTo(BuildStatus.SUCCESSFUL);
        assertThat(published.buildJobQueueItem().cloneToken()).isNull();
        verify(management).releaseBuildJob(future);
        verify(logs).removeBuildLogs("job");
    }

    @ParameterizedTest
    @ValueSource(strings = { "timeout", "timeout-message", "cancel", "cancel-other", "image", "other" })
    void failurePublishesExactStatusAndOnlyCountsAgentFailures(String type) {
        CompletableFuture<BuildResult> future = new CompletableFuture<>();
        when(management.executeBuildJob(any())).thenReturn(future);
        service.init();
        submit(buildJob(0));
        Throwable failure;
        BuildStatus status;
        int count;
        switch (type) {
            case "timeout" -> {
                failure = new RuntimeException("wrapped", new TimeoutException());
                status = BuildStatus.TIMEOUT;
                count = 1;
            }
            case "timeout-message" -> {
                failure = new RuntimeException("Build job with id job was timed out");
                status = BuildStatus.TIMEOUT;
                count = 1;
            }
            case "cancel" -> {
                failure = new RuntimeException("Build job with id job was cancelled.", new CancellationException());
                status = BuildStatus.CANCELLED;
                count = 0;
            }
            case "cancel-other" -> {
                failure = new RuntimeException("different cancellation", new CancellationException());
                status = BuildStatus.FAILED;
                count = 1;
            }
            case "image" -> {
                failure = new ImagePullException("missing exercise image");
                status = BuildStatus.FAILED;
                count = 0;
            }
            default -> {
                failure = new IllegalStateException("agent failed");
                status = BuildStatus.FAILED;
                count = 1;
            }
        }
        future.completeExceptionally(failure);
        ResultQueueItem published = results.poll();
        assertThat(published).isNotNull();
        assertThat(published.buildJobQueueItem().status()).isEqualTo(status);
        assertThat(published.buildResult().isBuildSuccessful()).isFalse();
        assertThat(processing.get("job")).isNull();
        verify(information).updateLocalBuildAgentInformationWithRecentJob(any(), eq(false), eq(false), eq(count));
        verify(management).releaseBuildJob(future);
        assertThat(service.isPaused()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 5 })
    void executorRejectionRequeuesWithinRetryLimit(int retries) {
        when(management.executeBuildJob(any())).thenThrow(new RejectedExecutionException("closed"));
        service.init();
        submit(buildJob(retries));
        assertThat(processing.get("job")).isNull();
        if (retries < 5) {
            var replacement = jobs.poll();
            assertThat(replacement.retryCount()).isEqualTo(1);
            assertThat(replacement.buildAgent().name()).isEmpty();
        }
        assertThat(jobs.isEmpty()).isTrue();
    }

    @Test
    void supersededAttemptCannotPublishOrRemoveReplacement() {
        CompletableFuture<BuildResult> future = new CompletableFuture<>();
        when(management.executeBuildJob(any())).thenReturn(future);
        service.init();
        submit(buildJob(0));
        BuildJobQueueItem replacement = new BuildJobQueueItem(processing.get("job"), new BuildAgentDTO("other", "address", "other"), 1);
        processing.put("job", replacement);
        future.complete(new BuildResult("main", "assignment", "tests", List.of(), true));
        assertThat(results.isEmpty()).isTrue();
        assertThat(processing.get("job")).isEqualTo(replacement);
        verify(management).releaseBuildJob(future);
    }

    @Test
    void unavailableCapacityAndDisconnectedClusterDoNotDequeue() {
        service.init();
        when(distributed.isConnectedToCluster()).thenReturn(false);
        submit(buildJob(0));
        when(distributed.isConnectedToCluster()).thenReturn(true);
        when(distributed.noDataMemberInClusterAvailable()).thenReturn(true);
        scheduledTasks.getFirst().run();
        when(distributed.noDataMemberInClusterAvailable()).thenReturn(false);
        when(configuration.getBuildExecutor()).thenReturn(null);
        scheduledTasks.getFirst().run();
        when(configuration.getBuildExecutor()).thenReturn(executor);
        when(executor.getActiveCount()).thenReturn(1);
        scheduledTasks.getFirst().run();
        when(executor.getActiveCount()).thenReturn(0);
        executor.getQueue().add(() -> {
        });
        scheduledTasks.getFirst().run();
        assertThat(jobs.size()).isEqualTo(1);
        verify(management, never()).executeBuildJob(any());
        service.setPauseState(true);
        executor.getQueue().clear();
        scheduledTasks.getFirst().run();
        assertThat(jobs.size()).isEqualTo(1);
    }

    @Test
    void disconnectedReconnectionRetriesOnceUntilInitializationSucceeds() {
        service.init();
        when(distributed.isConnectedToCluster()).thenReturn(false);
        connectionListener.get().accept(false);
        assertThat(scheduledTasks).hasSize(2);
        connectionListener.get().accept(false);
        assertThat(scheduledTasks).hasSize(2);
        scheduledTasks.getLast().run();
        assertThat(scheduledTasks).hasSize(2);
        when(distributed.isConnectedToCluster()).thenReturn(true);
        scheduledTasks.getLast().run();
        assertThat(scheduledTasks).hasSize(3);
        verify(scheduledFutures.get(1)).cancel(false);
        when(distributed.isInstanceRunning()).thenReturn(false);
        service.removeListenerAndCancelScheduledFuture();
        verify(scheduledFutures.getLast()).cancel(false);
    }

    @Test
    void informationUpdatesRequireClusterAndDataMemberAndIgnoreMalformedAgents() {
        when(distributed.isConnectedToCluster()).thenReturn(false);
        service.updateBuildAgentInformation();
        when(distributed.isConnectedToCluster()).thenReturn(true);
        when(distributed.noDataMemberInClusterAvailable()).thenReturn(true);
        service.updateBuildAgentInformation();
        verify(information, never()).updateLocalBuildAgentInformation(org.mockito.ArgumentMatchers.anyBoolean());
        when(distributed.noDataMemberInClusterAvailable()).thenReturn(false);
        agents.put("missing", null);
        agents.put("incomplete", new BuildAgentInformation(null, 1, 0, List.of(), BuildAgentStatus.IDLE, null, null, 0));
        service.updateBuildAgentInformation();
        verify(information).updateLocalBuildAgentInformation(false);
        assertThat(agents).containsKeys("missing", "incomplete");
    }

    @Test
    void staleDetectionSkipsPausedFetchingYoungAndUnknownStartJobs() {
        when(management.getRunningBuildJobIds()).thenReturn(Set.of("job"));
        when(distributed.isConnectedToCluster()).thenReturn(false);
        service.detectAndCleanupStaleBuildJobs();
        when(distributed.isConnectedToCluster()).thenReturn(true);
        service.setPauseState(true);
        service.detectAndCleanupStaleBuildJobs();
        service.setPauseState(false);
        when(runner.isFetchingImage("job")).thenReturn(true);
        service.detectAndCleanupStaleBuildJobs();
        when(runner.isFetchingImage("job")).thenReturn(false);
        processing.put("job", withStart(buildJob(0), null, null));
        service.detectAndCleanupStaleBuildJobs();
        var young = withStart(buildJob(0), ZonedDateTime.now(), new BuildAgentDTO("agent-one", "address", "agent"));
        processing.put("job", young);
        for (int i = 0; i < 8; i++) {
            service.detectAndCleanupStaleBuildJobs();
        }
        assertThat(processing.get("job")).isSameAs(young);
        verify(management, never()).cancelBuildJob(any());
        verify(information, never()).updateLocalBuildAgentInformation(org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void staleJobAtRetryLimitIsCancelledOnlyAfterSixChecksAndNeverRequeued() {
        var expired = withStart(buildJob(5), ZonedDateTime.now().minusMinutes(10), new BuildAgentDTO("agent-one", "address", "agent"));
        processing.put("job", expired);
        when(management.getRunningBuildJobIds()).thenReturn(Set.of("job"));
        when(distributed.getProcessingJobsForAgentByName("agent-one")).thenReturn(List.of(expired));
        for (int i = 0; i < 5; i++) {
            service.detectAndCleanupStaleBuildJobs();
        }
        verify(management, never()).cancelBuildJob(any());
        service.detectAndCleanupStaleBuildJobs();
        verify(management).cancelBuildJob("job");
        assertThat(processing.get("job")).isNull();
        assertThat(queuedJobs).isEmpty();
        verify(information).updateLocalBuildAgentInformation(false);
    }

    @Test
    void activeExecutionResetsStaleCounterAndOldLocalAttemptIsHandedOffWithoutTerminalResult() {
        var future = new CompletableFuture<BuildResult>();
        when(management.executeBuildJob(any())).thenReturn(future);
        service.init();
        var oldStart = ZonedDateTime.now().minusMinutes(10);
        try (var clock = mockStatic(ZonedDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(ZonedDateTime::now).thenReturn(oldStart);
            submit(buildJob(0));
        }
        when(management.getRunningBuildJobIds()).thenReturn(Set.of("job"));
        for (int i = 0; i < 3; i++) {
            service.detectAndCleanupStaleBuildJobs();
        }
        when(runner.isActive("job")).thenReturn(true);
        service.detectAndCleanupStaleBuildJobs();
        when(runner.isActive("job")).thenReturn(false);
        for (int i = 0; i < 5; i++) {
            service.detectAndCleanupStaleBuildJobs();
        }
        verify(management, never()).cancelBuildJob(any());
        service.detectAndCleanupStaleBuildJobs();
        verify(management).cancelBuildJob("job");
        assertThat(processing.get("job")).isNull();
        service.setPauseState(true);
        future.complete(new BuildResult("main", "assignment", "tests", List.of(), true));
        assertThat(results.isEmpty()).isTrue();
        assertThat(queuedJobs).singleElement().satisfies(job -> {
            assertThat(job.id()).isEqualTo("job");
            assertThat(job.retryCount()).isEqualTo(1);
            assertThat(job.buildAgent().name()).isEmpty();
        });
    }

    @Test
    void missingLocalAttemptCannotBeCancelledAndStoppedTrackingClearsDetectionHistory() {
        var expired = withStart(buildJob(0), ZonedDateTime.now().minusMinutes(10), new BuildAgentDTO("agent-one", "address", "agent"));
        processing.put("job", expired);
        when(management.getRunningBuildJobIds()).thenReturn(Set.of("job"));
        for (int i = 0; i < 5; i++) {
            service.detectAndCleanupStaleBuildJobs();
        }
        when(management.getRunningBuildJobIds()).thenReturn(Set.of());
        service.detectAndCleanupStaleBuildJobs();
        when(management.getRunningBuildJobIds()).thenReturn(Set.of("job"));
        for (int i = 0; i < 5; i++) {
            service.detectAndCleanupStaleBuildJobs();
        }
        verify(information, never()).updateLocalBuildAgentInformation(org.mockito.ArgumentMatchers.anyBoolean());
        service.detectAndCleanupStaleBuildJobs();
        assertThat(processing.get("job")).isSameAs(expired);
        assertThat(queuedJobs).isEmpty();
        verify(management, never()).cancelBuildJob(any());
        verify(information).updateLocalBuildAgentInformation(false);
    }

    private BuildJobQueueItem withStart(BuildJobQueueItem job, ZonedDateTime start, BuildAgentDTO agent) {
        return new BuildJobQueueItem(job.id(), job.name(), agent, job.participationId(), job.courseId(), job.exerciseId(), job.retryCount(), job.priority(), job.status(),
                job.repositoryInfo(), new JobTimingInfo(job.jobTimingInfo().submissionDate(), start, null, null, 0), job.buildConfig(), null, job.cloneToken());
    }

    private void submit(BuildJobQueueItem job) {
        jobs.add(job);
        scheduledTasks.getLast().run();
    }

    private BuildJobQueueItem buildJob(int retryCount) {
        var repositories = new RepositoryInfo("repository", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", null, new String[0], new String[0]);
        var timing = new JobTimingInfo(ZonedDateTime.now(), null, null, null, -1);
        var config = new BuildConfig("script", "image", "commit", "assignment", "tests", "main", ProgrammingLanguage.JAVA, null, false, false, List.of(), 60, "assignment", "tests",
                "solution", new DockerRunConfig(List.of(), null, 0, 0, 0));
        return new BuildJobQueueItem("job", "job", new BuildAgentDTO("", "", ""), 1, 2, 3, retryCount, 1, null, repositories, timing, config, null, "clone-token");
    }
}
