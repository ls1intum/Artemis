package de.tum.cit.aet.artemis.programming.icl;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDetailsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;

class LocalCIEventListenerServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localcieventlistenerint";

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    @Value("${artemis.continuous-integration.build-agent.display-name:}")
    private String buildAgentDisplayName;

    protected BuildAgentInformation buildAgent;

    protected IMap<String, BuildAgentInformation> buildAgentInformation;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void initBuildAgentInfo() {
        buildAgentInformation = hazelcastInstance.getMap("buildAgentInformation");
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        BuildAgentDTO buildAgentDTO = new BuildAgentDTO(buildAgentShortName, memberAddress, buildAgentDisplayName);
        buildAgent = new BuildAgentInformation(buildAgentDTO, 0, 0, new ArrayList<>(List.of()), BuildAgentInformation.BuildAgentStatus.IDLE, null, null, 100, 4);
        buildAgentInformation.put(memberAddress, buildAgent);
    }

    @AfterEach
    void clear() {
        buildAgentInformation.clear();
    }

    @Test
    void testSelfPauseTriggersListenerAndEmailNotification() {
        String memberAddress = buildAgent.buildAgent().memberAddress();
        int consecutiveFailedBuildJobs = 100;
        BuildAgentDetailsDTO updatedDetails = new BuildAgentDetailsDTO(0, 0, 0, 0, 0, 0, null, ZonedDateTime.now(), null, consecutiveFailedBuildJobs);
        BuildAgentInformation updatedInfo = new BuildAgentInformation(buildAgent.buildAgent(), buildAgent.maxNumberOfConcurrentBuildJobs(), buildAgent.numberOfCurrentBuildJobs(),
                buildAgent.runningBuildJobs(), BuildAgentInformation.BuildAgentStatus.SELF_PAUSED, buildAgent.publicSshKey(), updatedDetails,
                buildAgent.pauseAfterConsecutiveBuildFailures(), buildAgent.maxConcurrentBuildsAllowed());

        buildAgentInformation.put(memberAddress, updatedInfo);
        await().until(() -> buildAgentInformation.get(memberAddress).status() == BuildAgentInformation.BuildAgentStatus.SELF_PAUSED);
        verify(mailService, timeout(1000)).sendBuildAgentSelfPausedEmailToAdmin(any(User.class), eq(buildAgent.buildAgent().name()), eq(consecutiveFailedBuildJobs));
    }

}
