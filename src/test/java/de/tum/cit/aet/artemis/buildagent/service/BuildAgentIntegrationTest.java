package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
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
import org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiChatAutoConfiguration;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
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

        await().until(() -> {
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
            return buildAgent.status() == BuildAgentInformation.BuildAgentStatus.PAUSED;
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
            return buildAgent.status() == BuildAgentInformation.BuildAgentStatus.ACTIVE;
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

        await().until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent != null && buildAgent.status() == BuildAgentInformation.BuildAgentStatus.ACTIVE;
        });

        pauseBuildAgentTopic.publish(buildAgentShortName);

        await().until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent != null && buildAgent.status() == BuildAgentInformation.BuildAgentStatus.PAUSED;
        });

        await().until(() -> {
            var queued = buildJobQueue.peek();
            return queued != null && queued.id().equals(queueItem.id());
        });
    }

    @Test
    void testBuildAgentNoCommitHash() {
        var queueItem = createBuildJobQueueItemWithNoCommitHash();

        buildJobQueue.add(queueItem);

        await().until(() -> {
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
            return buildAgent != null && buildAgent.status() == BuildAgentInformation.BuildAgentStatus.SELF_PAUSED;
        });

        // resume and wait for unpause not interfere with other tests
        resumeBuildAgentTopic.publish(buildAgentShortName);
        await().until(() -> {
            var buildAgent = buildAgentInformation.get(distributedDataAccessService.getLocalMemberAddress());
            return buildAgent.status() != BuildAgentInformation.BuildAgentStatus.SELF_PAUSED;
        });
    }

    @Test
    void testSpringAIAutoConfigurationsExcluded() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> applicationContext.getBean(AzureOpenAiChatAutoConfiguration.class));
    }
}
