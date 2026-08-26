package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentAddressInfo;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkConfiguration;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

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

    /**
     * The agents this cluster knows about, by short name. The middleware reports connections by <em>its</em> client
     * name, which is the agent short name under Hazelcast and the node identity under Redis, so nothing is registered
     * for a client that matches no agent here.
     */
    private Map<String, BuildAgentInformation> registeredAgents;

    /**
     * What the agents report about themselves, which is how an origin is established where the middleware cannot
     * observe one - under Redis, whose clients connect to Redis rather than to a core node.
     */
    private Map<String, BuildAgentAddressInfo> reportedAddresses;

    @BeforeEach
    void setUp() {
        distributedDataAccessService = mock(DistributedDataAccessService.class);
        registeredAddresses = new HashMap<>();
        registeredAgents = new HashMap<>();
        reportedAddresses = new HashMap<>();

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
        when(agentInformationMap.get(any())).thenAnswer(invocation -> registeredAgents.get(invocation.getArgument(0)));
        when(agentInformationMap.values()).thenAnswer(_ -> List.copyOf(registeredAgents.values()));
        buildAgentInformation = agentInformationMap;

        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
        // The Hazelcast shape, where a client connects to a core node and its observed address is therefore also the
        // address its clone arrives from. shouldNotBindTheOriginWhenClientsConnectToTheMiddlewareInstead covers the other.
        when(distributedDataAccessService.clientsConnectDirectlyToCoreNodes()).thenReturn(true);
        when(distributedDataAccessService.getDistributedBuildAgentInformation()).thenReturn(buildAgentInformation);
        when(distributedDataAccessService.getProcessingJobsForAgentByName(any())).thenReturn(List.of());
        when(distributedDataAccessService.getDistributedBuildAgentAddresses()).thenReturn(map);
        when(distributedDataAccessService.getBuildAgentAddressMap()).thenAnswer(_ -> Map.copyOf(registeredAddresses));
        when(distributedDataAccessService.getBuildAgentReportedAddressMap()).thenAnswer(_ -> Map.copyOf(reportedAddresses));
    }

    /**
     * Stubs what the middleware reports and registers each observed client as a build agent of that name, which is the
     * Hazelcast shape: an agent's Hazelcast client name is its own short name.
     *
     * @param observedByClientName the middleware's client name to the addresses it is observed at
     */
    private void observe(Map<String, Set<String>> observedByClientName) {
        observedByClientName.keySet().forEach(this::registerAgent);
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(observedByClientName));
    }

    /**
     * Registers a build agent whose middleware client name differs from its short name, which is the Redis shape: the
     * client name is the node identity, and the agent publishes it as its member address.
     *
     * @param agentName     the build agent short name
     * @param memberAddress the middleware identity the agent reports for itself
     */
    private void registerAgentWithMemberAddress(String agentName, String memberAddress) {
        registeredAgents.put(agentName, new BuildAgentInformation(new BuildAgentDTO(agentName, memberAddress, agentName), 1, 0, List.of(), BuildAgentStatus.IDLE, null, null, 0));
    }

    private void registerAgent(String agentName) {
        registerAgentWithMemberAddress(agentName, "[127.0.0.1]:5701");
    }

    private BuildAgentAddressRegistryService createService(List<String> allowedRanges) {
        BuildAgentNetworkConfiguration configuration = new BuildAgentNetworkConfiguration();
        configuration.setAllowedRanges(allowedRanges);
        return new BuildAgentAddressRegistryService(distributedDataAccessService, new BuildAgentNetworkPolicy(configuration, new MockEnvironment()));
    }

    @Test
    void shouldRegisterTheAddressAnAgentConnectsFrom() {
        observe(Map.of("agent-1", Set.of("10.0.0.5"), "agent-2", Set.of("10.0.0.9")));
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
        observe(Map.of("agent-1", Set.of("10.0.0.5"), "agent-2", Set.of("10.0.0.5")));
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
        observe(Map.of("agent-1", Set.of("10.0.0.5", "10.0.0.6")));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.6")).isTrue();
    }

    @Test
    void shouldRemoveAnAgentThatDisconnected() {
        observe(Map.of("agent-1", Set.of("10.0.0.5"), "agent-2", Set.of("10.0.0.6")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        registeredAgents.remove("agent-2");
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(registeredAddresses).as("a stale address must not linger after the agent is gone").doesNotContainKey("agent-2");
    }

    @Test
    void shouldMarkAnAgentOutsideTheAllowlist() {
        observe(Map.of("agent-1", Set.of("203.0.113.9")));
        BuildAgentAddressRegistryService service = createService(List.of("10.0.0.0/8"));

        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses.get("agent-1").withinAllowlist()).as("an agent outside the configured networks is recorded as such, and BuildAgentNetworkPolicy refuses it")
                .isFalse();
    }

    @Test
    void shouldKeepAnAgentInsideTheAllowlist() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
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
        observe(Map.of());
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
        observe(Map.of("observed-agent", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("observed-agent", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("observed-agent", "203.0.113.9")).as("an observed agent is bound to where it was observed").isFalse();
        assertThat(service.isRegisteredAddressOfAgent("co-located-agent", "127.0.0.1")).as("an unobserved agent stays unconstrained").isTrue();
    }

    @Test
    void shouldNotWipeTheRegistryWhenTheProviderTemporarilyReportsNothing() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
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
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
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
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        // The agent is gone from the observed connections, but its registration has not been cleaned up yet.
        observe(Map.of());
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
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        observe(Map.of());
        registeredAgents.clear();
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
        observe(Map.of("agent-1", Set.of("::ffff:10.0.0.5"), "agent-2", Set.of("10.0.0.9")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("observed as IPv4-mapped, requested as IPv4").isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-2", "::ffff:10.0.0.9")).as("observed as IPv4, requested as IPv4-mapped").isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.6")).as("normalising must not make unrelated addresses match").isFalse();
    }

    @Test
    void shouldClearTheRegistryWhenTheProviderReportsNoClients() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        // Deregistered as well, so nothing is left that could still authenticate and warrant a denying tombstone
        observe(Map.of());
        registeredAgents.clear();
        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses).isEmpty();
    }

    /**
     * The same address written in its other textual form must still match, because the two sides are formatted by
     * different components: the middleware and the servlet container.
     */
    @Test
    void shouldMatchTheSameAddressInAnotherNotation() {
        observe(Map.of("agent-1", Set.of("0:0:0:0:0:0:0:1")));
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
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        // The agent reconnected from elsewhere without this node having reconciled yet.
        observe(Map.of("agent-1", Set.of("10.0.0.9")));

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.9")).as("the miss must trigger a refresh rather than refuse a build of a live agent").isTrue();
    }

    /**
     * Reached from inside an authorization decision, where an exception would deny a legitimate build, so neither a
     * missing argument nor an unusable address may do anything but return false.
     */
    @Test
    void shouldRefuseMissingOrUnusableArguments() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
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
        observe(Map.of("agent-1", Set.of("203.0.113.9")));
        BuildAgentAddressRegistryService service = createService(List.of("10.0.0.0/8"));

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.50"))
                .as("an address the agent was never observed at must not be accepted just because the agent is outside the allowlist").isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "203.0.113.9")).as("the observed address stays registered; the allowlist is applied to the request address")
                .isTrue();
        assertThat(registeredAddresses.get("agent-1").withinAllowlist()).as("the allowlist verdict is still recorded for the admin UI").isFalse();
    }

    /**
     * The distinction the three-way outcome exists for. A provider that cannot report client addresses is a working
     * deployment whose agents must stay unconstrained; a node that cannot reach the cluster at all has established
     * nothing and must not hand out that exemption. Denying costs nothing here, because the token and job-scope checks
     * that follow read the processing list from the same middleware and would fail too.
     */
    @Test
    void shouldRefuseAMissWhenNothingCouldBeObserved() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(false);

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("an address already in the snapshot is unaffected by an outage").isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-2", "10.0.0.9")).as("an agent that cannot be looked up must not be exempted").isFalse();
    }

    /**
     * A provider hiccup must not lift a binding that has already been established. The provider reports "cannot observe"
     * and "the query failed" identically, so a Redis timeout used to be read as a permanently unobservable deployment and
     * exempted the agent outright - handing a valid token or key the wrong-address bypass for the duration of the
     * hiccup, even though a perfectly good snapshot said where that agent connects from.
     */
    @Test
    void shouldKeepEnforcingTheLastGoodSnapshotWhenTheProviderStopsAnswering() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        // The query now fails or times out, which the provider can only report as "no answer".
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.empty());

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.99")).as("an address outside the preserved snapshot must still be refused").isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("and the observed address must keep working, or the hiccup fails every build").isTrue();
    }

    /**
     * The other reading of the same signal, which is why it cannot simply fail closed. A deployment whose provider never
     * supports the query has no snapshot to preserve and never will, so absence there really does mean "not observable"
     * and denying would refuse every build permanently rather than for the length of a hiccup. Told apart by whether an
     * answer was ever obtained on this node.
     */
    @Test
    void shouldStillExemptAgentsWhereTheProviderNeverAnswers() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.empty());
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        assertThat(service.isAddressObservationAvailable()).as("no answer was ever obtained, so this is the unsupported case").isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "203.0.113.9")).as("with nothing observable there is no address to prefer over another").isTrue();
    }

    /**
     * The sequential form of the same hole, which a "was there a reconcile recently?" test cannot catch. There is no
     * connect-side callback, so an agent can connect and publish itself immediately after a reconcile completes. A
     * request arriving in that gap must not be answered from the earlier observation: at that point the agent was not
     * yet connected, so its absence from the snapshot says nothing about whether its origin is observable.
     * <p>
     * Under a global debounce this returned true - no entry, so the not-observable exemption - from an address the agent
     * was never observed at.
     */
    @Test
    void shouldNotExemptAnAgentThatAppearedAfterTheLastReconcile() {
        observe(Map.of());
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();
        assertThat(registeredAddresses).as("nothing is connected yet").isEmpty();

        // The agent connects and publishes itself; no callback tells this node, and the scheduled reconcile is not due.
        observe(Map.of("agent-1", Set.of("10.0.0.5")));

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.99")).as("the decision must reconcile rather than reuse an observation taken before the agent connected")
                .isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("and the address it did connect from still works").isTrue();
    }

    /**
     * The race the coordinated refresh exists for. An agent that has just published itself is briefly absent from the
     * snapshot, and absence is what grants the not-observable exemption, so a request arriving in that window must not
     * be exempted just because another request happens to be doing the reconcile that would have registered the agent.
     * <p>
     * Under a compare-and-set debounce the loser did not wait: it decided on the snapshot the winner was replacing and
     * was admitted from an address the agent was never observed at. Both requests here present the wrong address, so
     * both must be refused, and the assertion fails against that earlier implementation.
     */
    @Test
    void shouldNotExemptAConcurrentRequestWhileAnotherIsRefreshing() throws Exception {
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicInteger providerCalls = new AtomicInteger();
        registerAgent("agent-1");
        when(distributedDataAccessService.getConnectedClientAddresses()).thenAnswer(_ -> {
            if (providerCalls.incrementAndGet() == 1) {
                providerEntered.countDown();
                assertThat(releaseProvider.await(10, TimeUnit.SECONDS)).as("the test must release the provider").isTrue();
            }
            return Optional.of(Map.of("agent-1", Set.of("10.0.0.5")));
        });
        BuildAgentAddressRegistryService service = createService(List.of());

        // Neither request comes from an address agent-1 was observed at, and the snapshot starts empty, so under the
        // previous implementation whichever request did not win the debounce would have taken the exemption.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> service.isRegisteredAddressOfAgent("agent-1", "10.0.0.99"));
            assertThat(providerEntered.await(10, TimeUnit.SECONDS)).as("the first request must reach the provider").isTrue();
            Future<Boolean> second = executor.submit(() -> service.isRegisteredAddressOfAgent("agent-1", "10.0.0.99"));
            // Give the second request time to get as far as it is going to get while the first still holds the refresh.
            Thread.sleep(200);
            releaseProvider.countDown();

            assertThat(first.get(20, TimeUnit.SECONDS)).as("the refreshing request must be refused, the address is not registered").isFalse();
            assertThat(second.get(20, TimeUnit.SECONDS)).as("the waiting request must be refused too, not exempted on the pre-refresh snapshot").isFalse();
        }
        finally {
            executor.shutdownNow();
        }
    }

    /**
     * The provider surfaces no client-connected callback, so the registry learns of a new agent from these two
     * listeners and the scheduled reconcile. Losing the registration would leave every reconnected agent waiting for
     * the next reconcile.
     */
    @Test
    void shouldSubscribeToConnectionAndDisconnectionEvents() {
        observe(Map.of());

        createService(List.of()).registerListeners();

        verify(distributedDataAccessService).addConnectionStateListener(any());
        verify(distributedDataAccessService).addClientDisconnectionListener(any());
    }

    /**
     * The Redis shape. Redis has no member/client split, so a node's client name is its node identity and cannot also
     * be the build agent short name on a node that is both core node and build agent - under the multi-node stacks the
     * two read {@code artemis-node-2} and {@code artemis-build-agent-2}. The agent publishes that identity as its
     * member address, which is what ties the two together.
     * <p>
     * Without this the registry would hold nothing on every Redis installation, every agent would fall through to the
     * not-observable exemption, and the origin binding would be silently inert on a supported backend.
     */
    @Test
    void shouldRegisterAnAgentWhoseMiddlewareClientNameIsItsNodeIdentity() {
        registerAgentWithMemberAddress("artemis-build-agent-2", "artemis-node-2");
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("artemis-node-2", Set.of("10.0.0.5"))));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("artemis-build-agent-2", "10.0.0.5")).as("the agent is bound to where its node's Redis client connects from").isTrue();
        assertThat(service.isRegisteredAddressOfAgent("artemis-build-agent-2", "10.0.0.6")).isFalse();
    }

    /**
     * A client that is no build agent - another core node's own connection, which under Redis looks exactly like an
     * agent's - must not become an entry. It would only be a row in the admin view for something that never clones.
     */
    @Test
    void shouldIgnoreAConnectedClientThatIsNotABuildAgent() {
        when(distributedDataAccessService.getConnectedClientAddresses()).thenReturn(Optional.of(Map.of("artemis-node-1", Set.of("10.0.0.5"))));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses).isEmpty();
    }

    /**
     * The automatic rate limit exemption. A build agent drives far more git traffic from one address than any person
     * does, so a per-address limit sized for people would throttle it; registering happens when the agent connects, so
     * the exemption follows the agents instead of being a list an operator has to maintain.
     */
    @Test
    void shouldRecogniseARegisteredAddressWithoutNamingTheAgent() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredBuildAgentAddress("10.0.0.5")).isTrue();
        assertThat(service.isRegisteredBuildAgentAddress("::ffff:10.0.0.5")).as("the two sides are formatted independently, so the mapped form has to match too").isTrue();
        assertThat(service.isRegisteredBuildAgentAddress("10.0.0.6")).isFalse();
        assertThat(service.isRegisteredBuildAgentAddress(null)).isFalse();
    }

    /**
     * A disconnected agent keeps a denying tombstone with no addresses, and that must not keep its former address
     * exempt: the exemption exists for the traffic of a live agent.
     */
    @Test
    void shouldStopRecognisingTheAddressOfADisconnectedAgent() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        observe(Map.of());
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredBuildAgentAddress("10.0.0.5")).isFalse();
    }

    /**
     * Redis is not a core node, so where it accepted a client connection from says nothing about the route that client
     * takes to the git server - and the two really do differ: with Redis in a container and the nodes on the host, the
     * middleware observes the docker bridge gateway while git sees loopback.
     * <p>
     * Enforcing that comparison refuses every clone, which is how this was found: a multi-node run on Redis produced
     * 279 refusals and no successful build. So the addresses are still observed and still shown, but the binding is
     * skipped exactly as it is where nothing can be observed at all.
     */
    @Test
    void shouldNotBindTheOriginWhenClientsConnectToTheMiddlewareInsteadOfACoreNode() {
        when(distributedDataAccessService.clientsConnectDirectlyToCoreNodes()).thenReturn(false);
        observe(Map.of("agent-1", Set.of("172.21.0.1")));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses).as("nothing may be registered from an observation that cannot speak for the git path").isEmpty();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "127.0.0.1")).as("the agent clones from a different path and must not be refused for it").isTrue();
    }

    /**
     * The middleware answers "who is connected" per node: Hazelcast's {@code getConnectedClients()} returns the clients
     * of the member it is asked. Every core node runs this reconcile against one shared map, so a node must not clear
     * an agent it has simply never seen - the agent's client may be attached to other members, and clearing denies its
     * clones on every node at once.
     * <p>
     * The case that makes this concrete is a core node joining an existing cluster: its first round sees no clients at
     * all, and without the guard it would blank every agent in the installation.
     */
    @Test
    void shouldNotClearAnAgentThisNodeHasNeverObserved() {
        // A different node registered the agent; this one has never had its client attached
        registerAgent("agent-1");
        registeredAddresses.put("agent-1", new BuildAgentAddressInfo("agent-1", Set.of("10.0.0.5"), ZonedDateTime.now(), true));
        observe(Map.of());
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses.get("agent-1").addresses()).as("an agent this node cannot see must keep the addresses the nodes that can see it registered")
                .containsExactly("10.0.0.5");
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
    }

    /**
     * The counterpart: once this node <em>has</em> seen the agent, its disappearance is a real disconnection and the
     * addresses have to stop authorizing clones.
     */
    @Test
    void shouldClearAnAgentThisNodeObservedAndThenLost() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        observe(Map.of());
        service.refreshRegisteredAddresses();

        assertThat(registeredAddresses.get("agent-1").addresses()).isEmpty();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isFalse();
    }

    /**
     * Establishes the origin where the middleware cannot: the agent reports the address a core node measured for it,
     * and is bound to that.
     * <p>
     * This is what gives Redis an origin binding at all. Its clients connect to Redis, not to a core node, so nothing
     * observed there can speak for the git path - but the agent asked a core node directly, so its answer can.
     */
    @Test
    void shouldBindAnAgentToTheAddressItReportsWhenNothingCanBeObserved() {
        when(distributedDataAccessService.clientsConnectDirectlyToCoreNodes()).thenReturn(false);
        registerAgent("agent-1");
        reportedAddresses.put("agent-1", new BuildAgentAddressInfo("agent-1", Set.of("10.0.0.5"), ZonedDateTime.now(), true));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("the reported address authorizes the agent").isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "203.0.113.9")).as("and every other address is refused, which is the whole point of the binding").isFalse();
    }

    /**
     * An agent that has not reported yet has no origin to be held to, so it stays unconstrained rather than being
     * refused - the same exemption an unobservable agent gets. Otherwise every agent would fail its first clones for
     * the length of one reporting round.
     */
    @Test
    void shouldNotConstrainAnAgentThatHasReportedNothing() {
        when(distributedDataAccessService.clientsConnectDirectlyToCoreNodes()).thenReturn(false);
        registerAgent("agent-1");
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "203.0.113.9")).isTrue();
    }

    /**
     * Where both sources know something, the agent is bound to either. They disagree exactly when a gateway sits on one
     * path and not the other, and the agent really does reach core nodes from both addresses.
     */
    @Test
    void shouldBindAnAgentToBothObservedAndReportedAddresses() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        reportedAddresses.put("agent-1", new BuildAgentAddressInfo("agent-1", Set.of("192.168.1.7"), ZonedDateTime.now(), true));
        BuildAgentAddressRegistryService service = createService(List.of());

        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "192.168.1.7")).isTrue();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "203.0.113.9")).isFalse();
    }

    /**
     * A reported entry keeps an agent bound even once it disconnects and its observed addresses are cleared: the
     * tombstone denies, and the reported address must not quietly re-open what the tombstone closed for a different
     * address.
     */
    @Test
    void shouldStillRefuseAnAddressNeitherSourceKnows() {
        observe(Map.of("agent-1", Set.of("10.0.0.5")));
        reportedAddresses.put("agent-1", new BuildAgentAddressInfo("agent-1", Set.of("192.168.1.7"), ZonedDateTime.now(), true));
        BuildAgentAddressRegistryService service = createService(List.of());
        service.refreshRegisteredAddresses();

        observe(Map.of());
        service.refreshRegisteredAddresses();

        assertThat(service.isRegisteredAddressOfAgent("agent-1", "10.0.0.5")).as("the observed address is gone with the connection").isFalse();
        assertThat(service.isRegisteredAddressOfAgent("agent-1", "192.168.1.7")).as("the reported one stands until it expires").isTrue();
    }

}
