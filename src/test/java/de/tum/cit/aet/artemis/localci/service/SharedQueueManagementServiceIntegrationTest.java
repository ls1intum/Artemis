package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;

class SharedQueueManagementServiceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final Duration ONE_YEAR = Duration.ofDays(365);

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    /**
     * The scheduled push clears the shared "dockerImageCleanupInfo" map before it refills it from the database. A test that fills the same map itself (for example
     * {@code BuildAgentDockerServiceTest}) would lose its entries whenever the push fires in between, so the test context configures the task to never fire on its own.
     */
    @Test
    void testScheduledPushOfDockerImageCleanupInfoNeverFiresOnItsOwnInTests() {
        // The runnable of a scheduled task is wrapped, its string form names the scheduled method.
        FixedRateTask pushTask = scheduledAnnotationBeanPostProcessor.getScheduledTasks().stream().map(ScheduledTask::getTask)
                .filter(task -> task instanceof FixedRateTask && task.getRunnable().toString().endsWith("SharedQueueManagementService.pushDockerImageCleanupInfo"))
                .map(FixedRateTask.class::cast).findFirst().orElseThrow();

        assertThat(pushTask.getIntervalDuration()).isGreaterThanOrEqualTo(ONE_YEAR);
        assertThat(pushTask.getInitialDelayDuration()).isGreaterThanOrEqualTo(ONE_YEAR);
    }

    @Test
    void testPushDockerImageCleanupInfo() {

        DistributedMap<String, ZonedDateTime> dockerImageCleanupInfo = distributedDataAccessService.getDistributedDockerImageCleanupInfo();
        dockerImageCleanupInfo.clear();

        ZonedDateTime now = ZonedDateTime.now();

        BuildJob b1 = new BuildJob();
        b1.setDockerImage("image1");
        b1.setBuildStartDate(now);
        buildJobRepository.save(b1);

        BuildJob b2 = new BuildJob();
        b2.setDockerImage("image2");
        b2.setBuildStartDate(now.plusMinutes(1));
        buildJobRepository.save(b2);

        BuildJob b3 = new BuildJob();
        b3.setDockerImage("image3");
        b3.setBuildStartDate(now.plusMinutes(2));
        buildJobRepository.save(b3);

        sharedQueueManagementService.pushDockerImageCleanupInfo();

        // Verify that the dockerImageCleanupInfo map contains three entries
        assertThat(dockerImageCleanupInfo.size()).isEqualTo(3);

        // Verify that the dockerImageCleanupInfo map contains the correct entries
        assertThat(dockerImageCleanupInfo.get("image1").getSecond()).isEqualTo(now.getSecond());
        assertThat(dockerImageCleanupInfo.get("image2").getSecond()).isEqualTo(now.plusMinutes(1).getSecond());
        assertThat(dockerImageCleanupInfo.get("image3").getSecond()).isEqualTo(now.plusMinutes(2).getSecond());
    }

    /**
     * Stores a build agent under {@code shortName} whose reported member address is {@code memberAddress}.
     *
     * @param shortName     the build agent short name, which is the key of the build agent information map
     * @param memberAddress the address the agent reports for itself, as returned by the distributed data provider
     */
    private void storeBuildAgent(String shortName, String memberAddress) {
        distributedDataAccessService.getDistributedBuildAgentInformation().put(shortName,
                new BuildAgentInformation(new BuildAgentDTO(shortName, memberAddress, shortName), 1, 0, List.of(), null, null, null, 0));
    }

    /**
     * The Hazelcast provider names its client after the build agent short name, so the disconnected client identifier
     * is already the map key.
     */
    @Test
    void testHandleClientDisconnectionRemovesAgentMatchedByKey() {
        DistributedMap<String, BuildAgentInformation> buildAgents = distributedDataAccessService.getDistributedBuildAgentInformation();
        buildAgents.clear();
        storeBuildAgent("artemis-build-agent-1", "[192.168.1.1]:5701");

        sharedQueueManagementService.handleClientDisconnection("artemis-build-agent-1");

        assertThat(buildAgents.get("artemis-build-agent-1")).isNull();
    }

    /**
     * The Redis provider identifies clients by {@code spring.data.redis.client-name}, which is unrelated to the build
     * agent short name used as the map key and only matches the stored member address. Removing by the reported
     * identifier alone therefore silently misses, leaving crashed agents in the map forever.
     */
    @Test
    void testHandleClientDisconnectionRemovesAgentMatchedByMemberAddress() {
        DistributedMap<String, BuildAgentInformation> buildAgents = distributedDataAccessService.getDistributedBuildAgentInformation();
        buildAgents.clear();
        storeBuildAgent("artemis-build-agent-1", "artemis-1001");

        sharedQueueManagementService.handleClientDisconnection("artemis-1001");

        assertThat(buildAgents.get("artemis-build-agent-1")).isNull();
    }

    @Test
    void testHandleClientDisconnectionKeepsOtherAgents() {
        DistributedMap<String, BuildAgentInformation> buildAgents = distributedDataAccessService.getDistributedBuildAgentInformation();
        buildAgents.clear();
        storeBuildAgent("artemis-build-agent-1", "artemis-1001");
        storeBuildAgent("artemis-build-agent-2", "artemis-1002");

        sharedQueueManagementService.handleClientDisconnection("artemis-1001");

        assertThat(buildAgents.get("artemis-build-agent-1")).isNull();
        assertThat(buildAgents.get("artemis-build-agent-2")).isNotNull();
    }
}
