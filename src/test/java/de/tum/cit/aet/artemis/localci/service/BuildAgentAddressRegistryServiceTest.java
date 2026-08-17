package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"), "agent-2", Set.of("10.0.0.9"))));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.6")).isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-2", "10.0.0.5")).as("an address registered for one agent must not authorize another observed agent").isFalse();
    }

    /**
     * Several agents on one host share a source address, which is normal under NAT and must not make them exclusive.
     */
    @Test
    void shouldAllowSeveralAgentsBehindOneAddress() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"), "agent-2", Set.of("10.0.0.5"))));
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
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5", "10.0.0.6"))));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.6")).isTrue();
    }

    @Test
    void shouldRemoveAnAgentThatDisconnected() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"), "agent-2", Set.of("10.0.0.6"))));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"))));
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(registeredAddresses).as("a stale address must not linger after the agent is gone").doesNotContainKey("agent-2");
    }

    @Test
    void shouldMarkAnAgentOutsideTheAllowlist() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("203.0.113.9"))));
        BuildAgentAddressRegistryService service = createService(List.of("10.0.0.0/8"));

        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses.get("agent-1").withinAllowlist()).as("an agent outside the configured networks is recorded as such, and BuildAgentNetworkPolicy refuses it")
                .isFalse();
    }

    @Test
    void shouldKeepAnAgentInsideTheAllowlist() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"))));
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
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.empty());
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isAddressObservationAvailable()).isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("an unanswerable question must not deny every build").isTrue();
    }

    /**
     * The single-node topology, which this check must not break. A build agent sharing a JVM with the core node opens no
     * client connection to the middleware, so the middleware answers with an empty list of clients and there is simply
     * nothing to observe for that agent. It has to stay unconstrained; treating "answered, but this agent is not in the
     * answer" as a rejection refused every clone on every single-node installation.
     */
    @Test
    void shouldNotConstrainAnAgentThatOpensNoClientConnection() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of()));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("co-located-agent", "127.0.0.1")).as("an agent with no observable cluster connection has no origin to check").isTrue();
    }

    /**
     * The other half of the per-agent rule: once an agent *is* observed somewhere, it is held to that address even
     * though other agents may be unobservable.
     */
    @Test
    void shouldStillConstrainAnObservedAgentWhileAnotherIsUnobservable() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("observed-agent", Set.of("10.0.0.5"))));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("observed-agent", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("observed-agent", "203.0.113.9")).as("an observed agent is bound to where it was observed").isFalse();
        assertThat(service.isRegisteredAddressOfAgent("co-located-agent", "127.0.0.1")).as("an unobserved agent stays unconstrained").isTrue();
    }

    @Test
    void shouldNotWipeTheRegistryWhenTheProviderTemporarilyReportsNothing() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"))));
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

    /**
     * The failure both reviewers of this change asked to be pinned down. Redis returns nothing when its CLIENT LIST
     * query times out, which is indistinguishable in the data from "no agent is connected". Treating it as the latter
     * would empty the registry and reject every clone in the cluster until a later successful refresh.
     */
    @Test
    void shouldKeepTheRegistryWhenTheProviderStopsBeingAbleToAnswer() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"))));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.empty());
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("a query that could not be answered must not look like every agent disconnecting").isTrue();
        assertThat(registeredAddresses).containsKey("agent-1");
    }

    /**
     * The opposite case, which must still work: the provider answered and genuinely reports nothing connected, so the
     * stale entry has to go.
     */
    @Test
    void shouldClearTheRegistryWhenTheProviderReportsNoClients() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"))));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of()));
        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses).isEmpty();
    }

    /**
     * The same address written in its other textual form must still match, because the two sides are formatted by
     * different components: the middleware and the servlet container.
     */
    @Test
    void shouldMatchTheSameAddressInAnotherNotation() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("0:0:0:0:0:0:0:1"))));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "::1")).as("::1 and 0:0:0:0:0:0:0:1 are the same address").isTrue();
    }
}
