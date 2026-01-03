package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.icl.DockerClientTestService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

// TestInstance.Lifecycle.PER_CLASS allows all test methods in this class to share the same instance of the test class.
// This reduces the overhead of repeatedly creating and tearing down a new Spring application context for each test method.
// This is especially useful when the test setup is expensive or when we want to share resources, such as database connections or mock objects, across multiple tests.
// In this case, we want to share the same GitService and UsernamePasswordCredentialsProvider.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

// ExecutionMode.SAME_THREAD ensures that all tests within this class are executed sequentially in the same thread, rather than in parallel or in a different thread.
// This is important in the context of LocalCI because it avoids potential race conditions or inconsistencies that could arise if multiple test methods are executed
// concurrently. For example, it prevents overloading the LocalCI's result processing system with too many build job results at the same time, which could lead to flaky tests
// or timeouts. By keeping everything in the same thread, we maintain more predictable and stable test behavior, while not increasing the test execution time significantly.
@Execution(ExecutionMode.SAME_THREAD)
class BuildAgentIntegrationTest extends AbstractArtemisBuildAgentTest {

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    @Value("${artemis.continuous-integration.pause-after-consecutive-failed-jobs}")
    private int pauseAfterConsecutiveFailures;

    private DistributedQueue<BuildJobQueueItem> buildJobQueue;

    private DistributedMap<String, BuildJobQueueItem> processingJobs;

    private DistributedQueue<ResultQueueItem> resultQueue;

    private DistributedMap<String, BuildAgentInformation> buildAgentInformation;

    private DistributedTopic<String> canceledBuildJobsTopic;

    private DistributedTopic<String> pauseBuildAgentTopic;

    private DistributedTopic<String> resumeBuildAgentTopic;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeAll
    void init() {
        processingJobs = distributedDataAccessService.getDistributedProcessingJobs();
        buildJobQueue = distributedDataAccessService.getDistributedBuildJobQueue();
        resultQueue = distributedDataAccessService.getDistributedBuildResultQueue();
        buildAgentInformation = distributedDataAccessService.getDistributedBuildAgentInformation();
        pauseBuildAgentTopic = distributedDataAccessService.getPauseBuildAgentTopic();
        resumeBuildAgentTopic = distributedDataAccessService.getResumeBuildAgentTopic();
        canceledBuildJobsTopic = distributedDataAccessService.getCanceledBuildJobsTopic();
        // this triggers the initialization of all required beans in the application context
        // in production the DeferredEagerBeanInitializer would do this automatically
        applicationContext.getBean(SharedQueueProcessingService.class);
    }

    @BeforeEach
    void clearQueues() {
        buildJobQueue.clear();
        processingJobs.clear();
        resultQueue.clear();
    }

    @Test
    void testBuildAgentBasicFlow() {
        var queueItem = createBaseBuildJobQueueItemForTrigger();

        buildJobQueue.add(queueItem);

        await().until(() -> {
            var processingJob = processingJobs.get(queueItem.id());
            return processingJob != null && processingJob.jobTimingInfo().buildStartDate() != null;
        });

        await().until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent.numberOfCurrentBuildJobs() == 1 && buildAgent.maxNumberOfConcurrentBuildJobs() == 2 && buildAgent.runningBuildJobs().size() == 1
                    && buildAgent.runningBuildJobs().getFirst().id().equals(queueItem.id());
        });

        await().until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });
    }

    @Test
    void testBuildAgentConcurrentBuilds() throws IOException {
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
        doAnswer(invocation -> {
            Thread.sleep(1000);
            return null;
        }).when(startContainerCmd).exec();
        // For this test, we need to return different test result streams for different containers. This is necessary since the first job would close the stream
        // which would cause the second job to fail. We need to return different streams for different containers to simulate concurrent builds.
        String image1 = "image1";
        String image2 = "image2";
        String container1 = "container1";
        String container2 = "container2";

        DockerClientTestService.mockCreateContainerCmd(dockerClient, container1, image1);
        DockerClientTestService.mockCreateContainerCmd(dockerClient, container2, image2);

        dockerClientTestService.mockTestResultsForContainer(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH,
                LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY, container1);
        dockerClientTestService.mockTestResultsForContainer(dockerClient, ALL_SUCCEED_TEST_RESULTS_PATH, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY,
                container2);

        var queueItem = createBaseBuildJobQueueItemForTriggerWithImage(image1);
        var queueItem2 = createBaseBuildJobQueueItemForTriggerWithImage(image2);

        buildJobQueue.add(queueItem);
        buildJobQueue.add(queueItem2);

        await().pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
            var processingJob = processingJobs.get(queueItem.id());
            var processingJob2 = processingJobs.get(queueItem2.id());
            return processingJob != null && processingJob.jobTimingInfo().buildStartDate() != null && processingJob2 != null
                    && processingJob2.jobTimingInfo().buildStartDate() != null;
        });

        await().pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent.numberOfCurrentBuildJobs() == 2 && buildAgent.maxNumberOfConcurrentBuildJobs() == 2 && buildAgent.runningBuildJobs().size() == 2
                    && (buildAgent.runningBuildJobs().getFirst().id().equals(queueItem.id()) || buildAgent.runningBuildJobs().getFirst().id().equals(queueItem2.id()))
                    && (buildAgent.runningBuildJobs().getLast().id().equals(queueItem.id()) || buildAgent.runningBuildJobs().getLast().id().equals(queueItem2.id()));
        });

        await().until(() -> {
            var resultQueueItem = resultQueue.poll();
            var resultQueueItem2 = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL && resultQueueItem2 != null
                    && resultQueueItem2.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL
                    && (resultQueueItem.buildJobQueueItem().id().equals(queueItem.id()) || resultQueueItem.buildJobQueueItem().id().equals(queueItem2.id()))
                    && (resultQueueItem2.buildJobQueueItem().id().equals(queueItem.id()) || resultQueueItem2.buildJobQueueItem().id().equals(queueItem2.id()));
        });
    }

    @Test
    void testBuildAgentErrorFlow() {
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
        when(startContainerCmd.exec()).thenThrow(new RuntimeException("Container start failed"));

        var queueItem = createBaseBuildJobQueueItemForTrigger();

        buildJobQueue.add(queueItem);

        await().until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id()) && resultQueueItem.buildJobQueueItem().status() == BuildStatus.FAILED;
        });
    }

    @Test
    void testBuildAgentTimeoutFlow() {
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
        doAnswer(invocation -> {
            Thread.sleep(5000);
            return null;
        }).when(startContainerCmd).exec();

        var queueItem = createBuildJobQueueItemForTimeout();

        buildJobQueue.add(queueItem);

        await().until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.TIMEOUT;
        });
    }

    @Test
    void testBuildAgentDisableNetwork() {
        var queueItem = createBuildJobQueueItemWithNetworkDisabled();

        buildJobQueue.add(queueItem);

        await().until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });
    }

    @Test
    void testBuildAgentJobCancelled() {
        // High timeout to ensure that the job is not finished before it is canceled. This will not affect the test runtime since the job is canceled.
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
        doAnswer(invocation -> {
            Thread.sleep(5000);
            return null;
        }).when(startContainerCmd).exec();

        var queueItem = createBaseBuildJobQueueItemForTrigger();

        buildJobQueue.add(queueItem);

        // poll delay will slow down tests, but will remove flaky to make sure that the job was added to the map before sending the cancellation message
        await().pollDelay(500, TimeUnit.MILLISECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
            var processingJob = processingJobs.get(queueItem.id());
            return processingJob != null && processingJob.jobTimingInfo().buildStartDate() != null;
        });

        canceledBuildJobsTopic.publish(queueItem.id());

        await().until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.CANCELLED;
        });
    }

    @Test
    void testBuildAgentPullImage() {
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        InspectImageResponse inspectImageResponse = new InspectImageResponse();
        inspectImageResponse.withArch("amd64");

        when(dockerClient.inspectImageCmd(anyString())).thenReturn(inspectImageCmd);

        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            if (callCount.incrementAndGet() <= 2) {
                throw new NotFoundException("Image not found");
            }
            else {
                return inspectImageResponse;
            }
        }).when(inspectImageCmd).exec();

        var queueItem = createBaseBuildJobQueueItemForTrigger();

        buildJobQueue.add(queueItem);

        await().atMost(60, TimeUnit.SECONDS).until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });
    }

    @Test
    void testPauseAndResumeBuildAgent() throws InterruptedException {
        pauseBuildAgentTopic.publish(buildAgentShortName);

        await().until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent.status() == BuildAgentStatus.PAUSED;
        });

        var queueItem = createBaseBuildJobQueueItemForTrigger();

        buildJobQueue.add(queueItem);

        await().pollDelay(100, TimeUnit.MILLISECONDS).until(() -> {
            var queueItemPolled = buildJobQueue.peek();
            return queueItemPolled != null && queueItemPolled.id().equals(queueItem.id());
        });

        resumeBuildAgentTopic.publish(buildAgentShortName);

        await().until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent.status() == BuildAgentStatus.ACTIVE;
        });

        await().until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });
    }

    @Test
    void testPauseBuildAgentBehavior() {
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
        doAnswer(invocation -> {
            Thread.sleep(5000);
            return null;
        }).when(startContainerCmd).exec();

        var queueItem = createBaseBuildJobQueueItemForTrigger();

        buildJobQueue.add(queueItem);

        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent != null && buildAgent.status() == BuildAgentStatus.ACTIVE;
        });

        pauseBuildAgentTopic.publish(buildAgentShortName);

        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent != null && buildAgent.status() == BuildAgentStatus.PAUSED;
        });

        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var queued = buildJobQueue.peek();
            return queued != null && queued.id().equals(queueItem.id());
        });
    }

    @Test
    void testBuildAgentNoCommitHash() {
        var queueItem = createBuildJobQueueItemWithNoCommitHash();

        buildJobQueue.add(queueItem);

        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });
    }

    @Test
    void testBuildAgentPullImageWithRandomNetworkFailure() {
        var inspectImageCmd = mock(InspectImageCmd.class);
        var inspectImageResponse = new InspectImageResponse().withArch("amd64");

        when(dockerClient.inspectImageCmd(anyString())).thenReturn(inspectImageCmd);
        AtomicInteger fails = new AtomicInteger(0);
        doAnswer(invocation -> {
            if (fails.incrementAndGet() <= 2) {
                throw new NotFoundException("Simulated network failure");
            }
            return inspectImageResponse;
        }).when(inspectImageCmd).exec();

        var queueItem = createBaseBuildJobQueueItemForTrigger();
        buildJobQueue.add(queueItem);

        await().until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });
    }

    @Test
    void testBuildAgentPausesAfterConsecutiveFailures() {
        // run 1 successful job to ensure no previous jobs failed already
        var queueItem = createBaseBuildJobQueueItemForTrigger();

        buildJobQueue.add(queueItem);

        await().until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });

        // then 5 failings jobs
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
        when(startContainerCmd.exec()).thenThrow(new RuntimeException("Container start failed"));

        for (int i = 0; i < pauseAfterConsecutiveFailures; i++) {
            buildJobQueue.add(createBaseBuildJobQueueItemForTrigger());
        }

        await().until(() -> resultQueue.size() >= pauseAfterConsecutiveFailures);

        await().until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent != null && buildAgent.status() == BuildAgentStatus.SELF_PAUSED;
        });

        // resume and wait for unpause not interfere with other tests
        resumeBuildAgentTopic.publish(buildAgentShortName);
        await().until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent.status() != BuildAgentStatus.SELF_PAUSED;
        });
    }

    /**
     * Verifies that Spring AI autoconfigurations are excluded for build agents.
     * Build agents should not load Spring AI beans to keep them lightweight.
     * Note: The 'local' profile enables hyperion, but in production build agents
     * use the 'buildagent' profile which explicitly disables it.
     * This test verifies the filtering mechanism works when properly configured.
     */
    @Test
    void testSpringAIAutoConfigurationsExcluded() {
        // When both Hyperion and Atlas are disabled (as they should be for build agents),
        // Spring AI autoconfigurations are filtered out by SpringAIAutoConfigurationFilter.
        // We verify this by checking that no ChatModel beans exist in the context.
        // Note: In test environment, 'local' profile may override buildagent settings,
        // so this test validates the mechanism rather than the exact production config.
        assertThat(applicationContext.getBeanNamesForType(ChatModel.class)).isEmpty();
    }

    /**
     * Test that the build agent successfully completes a build job when the tar archive copy operation
     * fails transiently but succeeds after retry. This simulates the scenario observed under high load
     * where "Could not create tar archive" exceptions occur due to transient I/O issues.
     */
    @Test
    void testBuildAgentRetryOnTarArchiveFailure() {
        // Mock copyArchiveToContainerCmd to fail on first 2 attempts, then succeed on 3rd attempt
        CopyArchiveToContainerCmd copyArchiveToContainerCmd = mock(CopyArchiveToContainerCmd.class);
        when(dockerClient.copyArchiveToContainerCmd(anyString())).thenReturn(copyArchiveToContainerCmd);
        when(copyArchiveToContainerCmd.withRemotePath(anyString())).thenReturn(copyArchiveToContainerCmd);
        when(copyArchiveToContainerCmd.withTarInputStream(any())).thenReturn(copyArchiveToContainerCmd);

        AtomicInteger copyAttempts = new AtomicInteger(0);
        doAnswer(invocation -> {
            int attempt = copyAttempts.incrementAndGet();
            // Fail on first 2 attempts (simulating transient I/O failure under load)
            // Since there are multiple copy operations (test repo, assignment repo, etc.),
            // we fail the first 2 calls to exec() across all copy operations
            if (attempt <= 2) {
                throw new RuntimeException("Simulated transient I/O failure: Could not create tar archive");
            }
            return null;
        }).when(copyArchiveToContainerCmd).exec();

        var queueItem = createBaseBuildJobQueueItemForTrigger();
        buildJobQueue.add(queueItem);

        // The build should eventually succeed after retries
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });

        // Verify that retries occurred (more than the minimum required calls)
        // Each repository copy requires one call, so if retries happened, we should have more calls
        assertThat(copyAttempts.get()).isGreaterThan(2);
    }

    /**
     * Test that the build agent properly fails a build job when all retry attempts for
     * tar archive operations are exhausted. This ensures proper error handling when
     * the infrastructure issue persists beyond the retry limit.
     */
    @Test
    void testBuildAgentFailsAfterAllTarArchiveRetriesExhausted() {
        // Mock copyArchiveToContainerCmd to always fail (simulating persistent I/O failure)
        CopyArchiveToContainerCmd copyArchiveToContainerCmd = mock(CopyArchiveToContainerCmd.class);
        when(dockerClient.copyArchiveToContainerCmd(anyString())).thenReturn(copyArchiveToContainerCmd);
        when(copyArchiveToContainerCmd.withRemotePath(anyString())).thenReturn(copyArchiveToContainerCmd);
        when(copyArchiveToContainerCmd.withTarInputStream(any())).thenReturn(copyArchiveToContainerCmd);

        // Always throw exception to simulate persistent failure
        doAnswer(invocation -> {
            throw new RuntimeException("Simulated persistent I/O failure: Could not create tar archive");
        }).when(copyArchiveToContainerCmd).exec();

        var queueItem = createBaseBuildJobQueueItemForTrigger();
        buildJobQueue.add(queueItem);

        // The build should fail after all retries are exhausted
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id()) && resultQueueItem.buildJobQueueItem().status() == BuildStatus.FAILED;
        });
    }

    /**
     * Test that the build agent successfully retrieves test results when the getArchiveFromContainer
     * operation fails transiently but succeeds after retry. This tests the retry mechanism for
     * copying archives FROM the container (as opposed to TO the container).
     */
    @Test
    void testBuildAgentRetryOnGetArchiveFromContainerFailure() throws IOException {
        // Mock copyArchiveFromContainerCmd to fail on first attempt, then succeed
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        when(dockerClient.copyArchiveFromContainerCmd(anyString(), anyString())).thenReturn(copyArchiveFromContainerCmd);

        AtomicInteger getArchiveAttempts = new AtomicInteger(0);
        doAnswer(invocation -> {
            int attempt = getArchiveAttempts.incrementAndGet();
            if (attempt == 1) {
                // Fail on first attempt
                throw new RuntimeException("Simulated transient failure retrieving archive from container");
            }
            // Succeed on subsequent attempts - return valid test results
            return dockerClientTestService.createInputStreamForTarArchiveFromMap(dockerClientTestService.createMapFromTestResultsFolder(PARTLY_SUCCESSFUL_TEST_RESULTS_PATH));
        }).when(copyArchiveFromContainerCmd).exec();

        var queueItem = createBaseBuildJobQueueItemForTrigger();
        buildJobQueue.add(queueItem);

        // The build should eventually succeed after retry
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });

        // Verify that at least one retry occurred
        assertThat(getArchiveAttempts.get()).isGreaterThan(1);
    }

    /**
     * Test that the build agent properly handles the case when copyArchiveFromContainerCmd
     * returns a null InputStream. This should be treated as a failure and trigger retry logic.
     */
    @Test
    void testBuildAgentHandlesNullInputStreamFromContainer() throws IOException {
        // Mock copyArchiveFromContainerCmd to return null on first attempt, then return valid stream
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        when(dockerClient.copyArchiveFromContainerCmd(anyString(), anyString())).thenReturn(copyArchiveFromContainerCmd);

        AtomicInteger attempts = new AtomicInteger(0);
        doAnswer(invocation -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                // Return null on first attempt to simulate edge case
                return null;
            }
            // Return valid stream on subsequent attempts
            return dockerClientTestService.createInputStreamForTarArchiveFromMap(dockerClientTestService.createMapFromTestResultsFolder(PARTLY_SUCCESSFUL_TEST_RESULTS_PATH));
        }).when(copyArchiveFromContainerCmd).exec();

        var queueItem = createBaseBuildJobQueueItemForTrigger();
        buildJobQueue.add(queueItem);

        // The build should eventually succeed after retry handles the null case
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id())
                    && resultQueueItem.buildJobQueueItem().status() == BuildStatus.SUCCESSFUL;
        });

        // Verify that retry occurred after null was returned
        assertThat(attempts.get()).isGreaterThan(1);
    }

    /**
     * Test that the build agent fails gracefully when getArchiveFromContainer consistently
     * fails after all retry attempts are exhausted.
     */
    @Test
    void testBuildAgentFailsAfterGetArchiveFromContainerRetriesExhausted() {
        // Mock copyArchiveFromContainerCmd to always fail
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        when(dockerClient.copyArchiveFromContainerCmd(anyString(), anyString())).thenReturn(copyArchiveFromContainerCmd);

        doAnswer(invocation -> {
            throw new RuntimeException("Simulated persistent failure retrieving archive from container");
        }).when(copyArchiveFromContainerCmd).exec();

        var queueItem = createBaseBuildJobQueueItemForTrigger();
        buildJobQueue.add(queueItem);

        // The build should fail after all retries are exhausted
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            var resultQueueItem = resultQueue.poll();
            return resultQueueItem != null && resultQueueItem.buildJobQueueItem().id().equals(queueItem.id()) && resultQueueItem.buildJobQueueItem().status() == BuildStatus.FAILED;
        });
    }
}
