package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentAddressInfo;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkConfiguration;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.localci.service.distributed.api.map.DistributedMap;

/**
 * Tests for {@link BuildAgentAddressRegistryService}.
 * <p>
 * Two behaviours matter beyond the bookkeeping. A provider that cannot observe client connections must be treated as
 * "unknown" rather than "nothing is connected", or every build on a Redis deployment without {@code CLIENT LIST}
 * access would be refused. And an agent that disconnects must lose its entry, or a stale address keeps authorizing
 * clones after the agent it belonged to is gone.
 */
class BuildAgentAddressRegistryServiceTest {

    private DistributedDataAccessService distributedDataAccessService;

    private Map<String, BuildAgentAddressInfo> registeredAddresses;

    @BeforeEach
    void setUp() {
        distributedDataAccessService = mock(DistributedDataAccessService.class);
        registeredAddresses = new HashMap<>();

        @SuppressWarnings("unchecked")
        DistributedMap<String, BuildAgentAddressInfo> map = mock(DistributedMap.class);
        when(map.get(any())).thenAnswer(invocation -> registeredAddresses.get(invocation.getArgument(0)));
        when(map.keySet()).thenAnswer(_ -> Set.copyOf(registeredAddresses.keySet()));
        when(map.getMapCopy()).thenAnswer(_ -> Map.copyOf(registeredAddresses));
        when(map.remove(any())).thenAnswer(invocation -> registeredAddresses.remove(invocation.getArgument(0)));
        // put returns void, so it has to be stubbed the other way round
        doAnswer(invocation -> registeredAddresses.put(invocation.getArgument(0), invocation.getArgument(1))).when(map).put(any(), any());

        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
        when(distributedDataAccessService.getDistributedBuildAgentAddresses()).thenReturn(map);
        when(distributedDataAccessService.getBuildAgentAddressMap()).thenAnswer(_ -> Map.copyOf(registeredAddresses));
    }

    private BuildAgentAddressRegistryService createService(List<String> allowedRanges) {
        BuildAgentNetworkConfiguration configuration = new BuildAgentNetworkConfiguration();
        configuration.setAllowedRanges(allowedRanges);
        return new BuildAgentAddressRegistryService(distributedDataAccessService, new BuildAgentNetworkPolicy(configuration));
    }

    @Test
    void shouldRegisterTheAddressAnAgentConnectsFrom() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.6")).isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-2", "10.0.0.5")).as("an address registered for one agent must not authorize another").isFalse();
    }

    /**
     * Several agents on one host share a source address, which is normal under NAT and must not make them exclusive.
     */
    @Test
    void shouldAllowSeveralAgentsBehindOneAddress() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Map.of("agent-1", Set.of("10.0.0.5"), "agent-2", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-2", "10.0.0.5")).isTrue();
    }

    /**
     * An agent reconnecting can briefly be observed under two addresses, so both must work rather than the newer one
     * evicting the older mid-build.
     */
    @Test
    void shouldAcceptEveryObservedAddressOfAnAgent() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Map.of("agent-1", Set.of("10.0.0.5", "10.0.0.6")));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.6")).isTrue();
    }

    @Test
    void shouldRemoveAnAgentThatDisconnected() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Map.of("agent-1", Set.of("10.0.0.5"), "agent-2", Set.of("10.0.0.6")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Map.of("agent-1", Set.of("10.0.0.5")));
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-2", "10.0.0.6")).as("a stale address must not keep authorizing clones after the agent is gone").isFalse();
        assertThat(registeredAddresses).doesNotContainKey("agent-2");
    }

    @Test
    void shouldMarkAnAgentOutsideTheAllowlist() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Map.of("agent-1", Set.of("203.0.113.9")));
        BuildAgentAddressRegistryService service = createService(List.of("10.0.0.0/8"));

        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses.get("agent-1").withinAllowlist()).isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "203.0.113.9")).as("an agent outside the configured networks must not be able to clone").isFalse();
    }

    @Test
    void shouldKeepAnAgentInsideTheAllowlist() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of("10.0.0.0/8"));

        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses.get("agent-1").withinAllowlist()).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
    }

    /**
     * The deliberate escape hatch. A provider that cannot report client addresses would otherwise look exactly like
     * one where no agent is connected, and denying every build is a far worse failure than not applying the binding.
     */
    @Test
    void shouldNotConstrainAnythingWhenTheProviderCannotObserveAddresses() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Map.of());
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isAddressObservationAvailable()).isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("an unanswerable question must not deny every build").isTrue();
    }

    @Test
    void shouldNotWipeTheRegistryWhenTheProviderTemporarilyReportsNothing() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        when(distributedDataAccessService.getConnectedClientAddresses()).thenThrow(new IllegalStateException("cluster hiccup"));
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("a failed refresh keeps the previous snapshot rather than stopping every build").isTrue();
    }

    @Test
    void shouldDoNothingWhenNotConnectedToTheCluster() {
        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(false);
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses).isEmpty();
    }
}
