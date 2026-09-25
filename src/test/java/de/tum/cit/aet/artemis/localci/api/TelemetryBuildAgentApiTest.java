package de.tum.cit.aet.artemis.localci.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider.ClusterMembership;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

class TelemetryBuildAgentApiTest {

    private final DistributedDataAccessService data = mock(DistributedDataAccessService.class);

    private final DistributedDataProvider provider = mock(DistributedDataProvider.class);

    private final LocalCITelemetryApi api = new LocalCITelemetryApi(data, provider);

    private BuildAgentInformation agent(String name, String address) {
        return new BuildAgentInformation(new BuildAgentDTO(name, address, name), 1, 0, java.util.List.of(), de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus.PAUSED, null,
                null, 0);
    }

    @Test
    void countsConnectedClientsAndColocatedAgentsButNotStaleEntries() {
        when(provider.getClusterMembership()).thenReturn(new ClusterMembership(Set.of("client"), Set.of("core")));
        when(data.getBuildAgentInformationMap())
                .thenReturn(Map.of("client", agent("client", "client-address"), "core", agent("colocated", "core"), "stale", agent("stale", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isEqualTo(2);
    }

    @Test
    void excludesStaleAgentsWhenHealthyHazelcastHasNoClients() {
        when(provider.getClusterMembership()).thenReturn(new ClusterMembership(Set.of(), Set.of("core")));
        when(provider.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of()));
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("core", agent("colocated", "core"), "stale", agent("stale", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isEqualTo(1);
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("stale", agent("stale", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isZero();
    }

    @Test
    void handlesRedisIdentityAndUnknownMembership() {
        when(provider.getClusterMembership()).thenReturn(new ClusterMembership(Set.of(), Set.of("redis-agent")));
        when(provider.buildAgentsAppearInClusterMemberList()).thenReturn(true);
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("agent", agent("short-name", "redis-agent"), "stale", agent("gone", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isEqualTo(1);
        when(provider.getClusterMembership()).thenReturn(new ClusterMembership(Set.of(), Set.of()));
        assertThat(api.getConnectedBuildAgentCount()).isNull();
    }

    @Test
    void doesNotTreatUnknownClientConnectivityAsZero() {
        when(provider.getClusterMembership()).thenReturn(new ClusterMembership(Set.of(), Set.of("core")));
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("agent", agent("client", "client-address")));
        assertThat(api.getConnectedBuildAgentCount()).isNull();
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of());
        assertThat(api.getConnectedBuildAgentCount()).isZero();
    }
}
