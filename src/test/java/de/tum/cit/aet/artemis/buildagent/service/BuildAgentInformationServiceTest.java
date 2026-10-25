package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.GitProperties;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.DockerClientTestService;
import de.tum.cit.aet.artemis.localci.service.distributed.local.LocalMap;

@ExtendWith(MockitoExtension.class)
class BuildAgentInformationServiceTest {

    private static final String AGENT_NAME = "test-agent";

    @Mock
    private BuildAgentConfiguration configuration;

    @Mock
    private BuildAgentSshKeyService sshKeyService;

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    @ParameterizedTest
    @EnumSource(value = BuildAgentStatus.class, names = { "PAUSED", "SELF_PAUSED" })
    void dockerVersionRefreshPreservesPauseStateAndFailureCount(BuildAgentStatus pausedStatus) {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var agents = new LocalMap<String, BuildAgentInformation>(executor);
            when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
            when(distributedDataAccessService.getDistributedBuildAgentInformation()).thenReturn(agents);
            when(distributedDataAccessService.getProcessingJobs()).thenReturn(List.of());

            var dockerClient = mock(DockerClient.class);
            when(configuration.getDockerClient()).thenReturn(dockerClient);
            DockerClientTestService.mockVersionCmd(dockerClient, "25.0.0-test");

            var service = new BuildAgentInformationService(configuration, sshKeyService, distributedDataAccessService, new GitProperties(new Properties()));
            ReflectionTestUtils.setField(service, "buildAgentShortName", AGENT_NAME);
            ReflectionTestUtils.setField(service, "buildAgentDisplayName", "Test Agent");
            service.updateLocalBuildAgentInformation(true, pausedStatus == BuildAgentStatus.SELF_PAUSED, 7);

            service.updateDockerVersion();

            var agent = agents.get(AGENT_NAME);
            assertThat(agent.status()).isEqualTo(pausedStatus);
            assertThat(agent.buildAgentDetails().consecutiveBuildFailures()).isEqualTo(7);
            assertThat(agent.buildAgentDetails().dockerVersion()).isEqualTo("25.0.0-test");
        }
    }
}
