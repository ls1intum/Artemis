package de.tum.cit.aet.artemis.localci.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

class TelemetryBuildAgentApiTest {

    private final DistributedDataAccessService data = mock(DistributedDataAccessService.class);

    private final DistributedDataProvider provider = mock(DistributedDataProvider.class);

    private final LocalCITelemetryApi api = new LocalCITelemetryApi(data, provider);

    private BuildAgentInformation agent(String name, String address) {
        return new BuildAgentInformation(new BuildAgentDTO(name, address, name), 1, 0, List.of(), BuildAgentStatus.PAUSED, null, null, 0);
    }

    @Test
    void countsConnectedClientsAndColocatedAgentsButNotStaleEntries() {
        when(provider.getClusterMemberAddresses()).thenReturn(Set.of("client-address", "core"));
        when(data.getBuildAgentInformationMap())
                .thenReturn(Map.of("client", agent("client", "client-address"), "core", agent("colocated", "core"), "stale", agent("stale", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isEqualTo(2);
    }

    @Test
    void excludesAgentsAbsentFromKnownMembership() {
        when(provider.getClusterMemberAddresses()).thenReturn(Set.of("core"));
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("core", agent("colocated", "core"), "stale", agent("stale", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isEqualTo(1);
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("stale", agent("stale", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isZero();
    }

    @Test
    void handlesRedisIdentityAndUnknownMembership() {
        when(provider.getClusterMemberAddresses()).thenReturn(Set.of("redis-agent"));
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("agent", agent("short-name", "redis-agent"), "stale", agent("gone", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isEqualTo(1);
        when(provider.getClusterMemberAddresses()).thenReturn(Set.of());
        assertThat(api.getConnectedBuildAgentCount()).isNull();
    }

}
