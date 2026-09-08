package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;

class LocalCIServiceTest {

    @Test
    void health_includesHyperionSandboxCapacityForAdminVisibility() {
        DistributedDataAccessService distributedDataAccessService = mock(DistributedDataAccessService.class);
        LocalCIService service = new LocalCIService(mock(BuildPhasesTemplateService.class), distributedDataAccessService, mock(ProgrammingExerciseBuildConfigRepository.class));
        BuildAgentDTO buildAgent = new BuildAgentDTO("agent-1", "127.0.0.1", "Agent One");
        BuildAgentInformation agentInformation = new BuildAgentInformation(buildAgent, 4, 1, List.of(), BuildAgentStatus.ACTIVE, null, null, 100, 2, 6);
        when(distributedDataAccessService.getBuildAgentInformation()).thenReturn(List.of(agentInformation));

        ConnectorHealth health = service.health();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> buildAgents = (List<Map<String, Object>>) health.additionalInfo().get("buildAgents");
        assertThat(buildAgents).singleElement().satisfies(agent -> {
            assertThat(agent).containsOnlyKeys("name", "displayName", "memberAddress", "status", "currentJobs", "maxJobs", "reservedGenerationSandboxSlots",
                    "maxGenerationSandboxSlots", "runningJobs");
            assertThat(agent).containsEntry("name", "agent-1").containsEntry("displayName", "Agent One").containsEntry("memberAddress", "127.0.0.1")
                    .containsEntry("status", "ACTIVE").containsEntry("currentJobs", 1).containsEntry("maxJobs", 4).containsEntry("reservedGenerationSandboxSlots", 2)
                    .containsEntry("maxGenerationSandboxSlots", 6).containsEntry("runningJobs", List.of());
        });
    }
}
