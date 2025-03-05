package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.hazelcast.collection.IQueue;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
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

    private static final Logger log = LoggerFactory.getLogger(BuildAgentIntegrationTest.class);

    private IQueue<BuildJobQueueItem> buildJobQueue;

    private IMap<String, BuildJobQueueItem> processingJobs;

    private IQueue<ResultQueueItem> resultQueue;

    private IMap<String, BuildAgentInformation> buildAgentInformation;

    private ITopic<String> canceledBuildJobsTopic;

    private ITopic<String> pauseBuildAgentTopic;

    private ITopic<String> resumeBuildAgentTopic;

    @BeforeAll
    void init() {
        processingJobs = this.hazelcastInstance.getMap("processingJobs");
        buildJobQueue = this.hazelcastInstance.getQueue("buildJobQueue");
        resultQueue = this.hazelcastInstance.getQueue("buildResultQueue");
        buildAgentInformation = this.hazelcastInstance.getMap("buildAgentInformation");
        canceledBuildJobsTopic = hazelcastInstance.getTopic("canceledBuildJobsTopic");
        pauseBuildAgentTopic = hazelcastInstance.getTopic("pauseBuildAgentTopic");
        resumeBuildAgentTopic = hazelcastInstance.getTopic("resumeBuildAgentTopic");
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
            var buildAgent = buildAgentInformation.get(hazelcastInstance.getCluster().getLocalMember().getAddress().toString());
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
    void testBuildAgentJobCancelledBeforeStarting() {
        var queueItem = createBaseBuildJobQueueItemForTrigger();

        buildJobQueue.add(queueItem);

        await().until(() -> {
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
            var buildAgent = buildAgentInformation.get(hazelcastInstance.getCluster().getLocalMember().getAddress().toString());
            return buildAgent.status() == BuildAgentInformation.BuildAgentStatus.PAUSED;
        });

        var queueItem = createBaseBuildJobQueueItemForTrigger();

        buildJobQueue.add(queueItem);

        Thread.sleep(100);

        assertThat(buildJobQueue.peek()).isNotNull();
        assertThat(buildJobQueue.peek().id()).isEqualTo(queueItem.id());

        resumeBuildAgentTopic.publish(buildAgentShortName);

        await().until(() -> {
            var buildAgent = buildAgentInformation.get(hazelcastInstance.getCluster().getLocalMember().getAddress().toString());
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
            var buildAgent = buildAgentInformation.get(hazelcastInstance.getCluster().getLocalMember().getAddress().toString());
            return buildAgent != null && buildAgent.status() == BuildAgentInformation.BuildAgentStatus.ACTIVE;
        });

        pauseBuildAgentTopic.publish(buildAgentShortName);

        await().until(() -> {
            var buildAgent = buildAgentInformation.get(hazelcastInstance.getCluster().getLocalMember().getAddress().toString());
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
}
