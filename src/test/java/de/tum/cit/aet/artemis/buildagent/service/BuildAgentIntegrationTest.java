package de.tum.cit.aet.artemis.buildagent.service;

import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.hazelcast.collection.IQueue;
import com.hazelcast.map.IMap;

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

    private IQueue<BuildJobQueueItem> buildJobQueue;

    private IMap<String, BuildJobQueueItem> processingJobs;

    private IQueue<ResultQueueItem> resultQueue;

    private IMap<String, BuildAgentInformation> buildAgentInformation;

    @BeforeAll
    void init() {
        processingJobs = this.hazelcastInstance.getMap("processingJobs");
        buildJobQueue = this.hazelcastInstance.getQueue("buildJobQueue");
        resultQueue = this.hazelcastInstance.getQueue("buildResultQueue");
        buildAgentInformation = this.hazelcastInstance.getMap("buildAgentInformation");
    }

    @Test
    void testBuildAgent() {
        var queueItem = createBaseBuildJobQueueItemForTrigger();

        // Add the build job to the queue
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
}
