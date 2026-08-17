package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentAddressInfo;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
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

    /**
     * The agent registration map, consulted when an agent disappears from the observed connections: an agent still
     * listed here can still authenticate, so its address entry has to be kept as a denying tombstone rather than
     * deleted.
     */
    private DistributedMap<String, BuildAgentInformation> buildAgentInformation;

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

        @SuppressWarnings("unchecked")
        DistributedMap<String, BuildAgentInformation> agentInformationMap = mock(DistributedMap.class);
        buildAgentInformation = agentInformationMap;

        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
        when(distributedDataAccessService.getDistributedBuildAgentInformation()).thenReturn(buildAgentInformation);
        when(distributedDataAccessService.getProcessingJobsForAgentByName(any())).thenReturn(List.of());
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
    /**
     * The window between an agent disconnecting and its registration and jobs being cleaned up, which different
     * listeners do. Deleting the address entry here would flip the agent from constrained to unconstrained, because an
     * absent entry means "origin not observable" and permits any address - so a leaked key or token would still name a
     * known agent, still match a listed job, and no longer be bound to an address. The entry therefore stays, with no
     * addresses, and denies.
     */
    @Test
    void shouldDenyADisconnectedAgentThatIsStillRegistered() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"))));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        // The agent is gone from the observed connections, but its registration has not been cleaned up yet.
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of()));
        when(buildAgentInformation.get("agent-1")).thenReturn(mock(BuildAgentInformation.class));
        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses).as("the entry must survive as a tombstone rather than be deleted").containsKey("agent-1");
        assertThat(registeredAddresses.get("agent-1").addresses()).isEmpty();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("its former address must stop working").isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.50")).as("and it must not become exempt from origin binding either").isFalse();
    }

    /**
     * The other end of that lifecycle: once the agent is neither registered nor holding jobs it cannot authenticate
     * anything, so keeping a tombstone would only grow the map.
     */
    @Test
    void shouldDropTheEntryOnceTheAgentIsFullyGone() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"))));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of()));
        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses).isEmpty();
    }

    /**
     * A dual-stack socket reports an IPv4 client as an IPv4-mapped IPv6 address, so the two sides of this comparison
     * legitimately disagree on notation. Neither string equality nor {@code IPAddress.equals} bridges that, and getting
     * it wrong refuses a working build agent.
     */
    @Test
    void shouldMatchAnIpv4MappedIpv6AddressAgainstItsIpv4Form() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("::ffff:10.0.0.5"), "agent-2", Set.of("10.0.0.9"))));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("observed as IPv4-mapped, requested as IPv4").isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-2", "::ffff:10.0.0.9")).as("observed as IPv4, requested as IPv4-mapped").isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.6")).as("normalising must not make unrelated addresses match").isFalse();
    }

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

    /**
     * An agent that reconnects gets a new source port and, behind NAT, possibly a new address, while it starts claiming
     * jobs immediately. Waiting for the scheduled reconcile would refuse its clones for up to that interval, so a miss
     * refreshes once before deciding.
     */
    @Test
    void shouldRefreshOnceBeforeRefusingAnUnknownAddress() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"))));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        // The agent reconnected from elsewhere without this node having reconciled yet.
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.9"))));

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.9")).as("the miss must trigger a refresh rather than refuse a build of a live agent").isTrue();
    }

    /**
     * Reached from inside an authorization decision, where an exception would deny a legitimate build, so neither a
     * missing argument nor an unusable address may do anything but return false.
     */
    @Test
    void shouldRefuseMissingOrUnusableArguments() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("10.0.0.5"))));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent(null, "10.0.0.5")).isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", null)).isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "not-an-address")).isFalse();
    }

    /**
     * An agent observed outside the allowlist must keep its observed address in the snapshot, because absence from the
     * snapshot means "origin not observable" and permits every address. Dropping it would make the agent flagged as
     * suspicious the only one with no origin binding, and its credential usable from anywhere inside the allowed
     * ranges - the exact inversion of what the allowlist is for.
     */
    @Test
    void shouldNotExemptAnAgentObservedOutsideTheAllowlistFromOriginBinding() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("agent-1", Set.of("203.0.113.9"))));
        BuildAgentAddressRegistryService service = createService(List.of("10.0.0.0/8"));

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.50"))
                .as("an address the agent was never observed at must not be accepted just because the agent is outside the allowlist").isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "203.0.113.9")).as("the observed address stays registered; the allowlist is applied to the request address")
                .isTrue();
        assertThat(registeredAddresses.get("agent-1").withinAllowlist()).as("the allowlist verdict is still recorded for the admin UI").isFalse();
    }

    /**
     * The provider surfaces no client-connected callback, so the registry learns of a new agent from these two
     * listeners and the scheduled reconcile. Losing the registration would leave every reconnected agent waiting for
     * the next reconcile.
     */
    @Test
    void shouldSubscribeToConnectionAndDisconnectionEvents() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of()));

        createService(List.of()).registerListeners();

        verify(distributedDataAccessService).addConnectionStateListener(any());
        verify(distributedDataAccessService).addClientDisconnectionListener(any());
    }
}
