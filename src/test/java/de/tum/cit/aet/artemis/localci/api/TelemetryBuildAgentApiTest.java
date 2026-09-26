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
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.distributed.api.DistributedDataProvider;

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
        when(provider.getConnectedClientNamesIfAvailable()).thenReturn(Optional.of(Set.of("client")));
        when(provider.getClusterMemberAddresses()).thenReturn(Set.of("core"));
        when(data.getBuildAgentInformationMap())
                .thenReturn(Map.of("client", agent("client", "client-address"), "core", agent("colocated", "core"), "stale", agent("stale", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isEqualTo(2);
    }

    @Test
    void excludesStaleAgentsWhenHealthyHazelcastHasNoClients() {
        when(provider.getClusterMemberAddresses()).thenReturn(Set.of("core"));
        when(provider.getConnectedClientNamesIfAvailable()).thenReturn(Optional.of(Set.of()));
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("core", agent("colocated", "core"), "stale", agent("stale", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isEqualTo(1);
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("stale", agent("stale", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isZero();
    }

    @Test
    void handlesRedisIdentityAndUnknownMembership() {
        when(provider.getClusterMemberAddresses()).thenReturn(Set.of("redis-agent"));
        when(provider.getConnectedClientNamesIfAvailable()).thenReturn(Optional.of(Set.of("redis-agent")));
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("agent", agent("short-name", "redis-agent"), "stale", agent("gone", "gone")));
        assertThat(api.getConnectedBuildAgentCount()).isEqualTo(1);
        when(provider.getClusterMemberAddresses()).thenReturn(Set.of());
        when(provider.getConnectedClientNamesIfAvailable()).thenReturn(Optional.empty());
        assertThat(api.getConnectedBuildAgentCount()).isNull();
    }

    @Test
    void doesNotTreatUnknownClientConnectivityAsZero() {
        when(provider.getClusterMemberAddresses()).thenReturn(Set.of("core"));
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("agent", agent("client", "client-address")));
        assertThat(api.getConnectedBuildAgentCount()).isNull();
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of());
        assertThat(api.getConnectedBuildAgentCount()).isZero();
    }

    @Test
    void localProviderCountsOnlyColocatedAgents() {
        var local = new de.tum.cit.aet.artemis.localci.service.distributed.local.LocalDataProviderService();
        when(data.getBuildAgentInformationMap()).thenReturn(Map.of("local", agent("local", "localhost"), "stale", agent("stale", "gone")));
        assertThat(new LocalCITelemetryApi(data, local).getConnectedBuildAgentCount()).isEqualTo(1);
    }

    @Test
    void hazelcastProviderDistinguishesEmptyClientsFromUnavailableLookup() {
        var hazelcast = mock(com.hazelcast.core.HazelcastInstance.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        var provider = new de.tum.cit.aet.artemis.localci.service.distributed.hazelcast.HazelcastDistributedDataProviderService(hazelcast);
        when(hazelcast.getLifecycleService().isRunning()).thenReturn(true);
        when(hazelcast.getClientService().getConnectedClients()).thenReturn(java.util.List.of());
        assertThat(provider.getConnectedClientNamesIfAvailable()).hasValue(Set.of());
        var client = mock(com.hazelcast.client.Client.class);
        when(client.getName()).thenReturn("agent");
        when(hazelcast.getClientService().getConnectedClients()).thenReturn(java.util.List.of(client));
        assertThat(provider.getConnectedClientNamesIfAvailable()).hasValue(Set.of("agent"));
        when(hazelcast.getClientService()).thenThrow(new UnsupportedOperationException());
        assertThat(provider.getConnectedClientNamesIfAvailable()).isEmpty();
        when(hazelcast.getLifecycleService().isRunning()).thenReturn(false);
        assertThat(provider.getConnectedClientNamesIfAvailable()).isEmpty();
    }

    @Test
    void defaultProviderDoesNotConfuseFailedRedisLookupWithNoAgents() {
        var provider = mock(DistributedDataProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(provider.getConnectedClientNames()).thenReturn(Set.of());
        assertThat(provider.getConnectedClientNamesIfAvailable()).isEmpty();
        when(provider.getConnectedClientNames()).thenReturn(Set.of("artemis-agent"));
        assertThat(provider.getConnectedClientNamesIfAvailable()).hasValue(Set.of("artemis-agent"));
    }

}
