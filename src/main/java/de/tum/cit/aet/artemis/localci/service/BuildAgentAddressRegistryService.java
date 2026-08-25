package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentAddressInfo;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.util.IpAddresses;

/**
 * Records which network addresses each build agent connects to the cluster from, so that the git authorization paths
 * can require a build agent's clone to come from an address that agent is actually connected from.
 * <p>
 * The <b>addresses</b> are observed by the middleware on the agent's own cluster connection, never reported by the
 * agent. That is the point: {@code BuildAgentDTO.memberAddress} is the agent's view of its local socket, which is
 * pre-NAT and which a hostile agent can set to anything, whereas an observed address is where the connection actually
 * came from. Writing the observation into a distributed map lets any core node authorize a git request from its own
 * local snapshot, without a provider-specific call on the hot path and without caring which node the agent is
 * attached to.
 * <p>
 * The <b>name</b> the addresses are keyed by is not authenticated. It is the middleware's client name, which the client
 * chooses for itself in both providers (the Hazelcast instance name, or {@code CLIENT SETNAME} under Redis), so a node
 * that has joined the cluster could register its address under another agent's name. That costs nothing extra: such a
 * node can already read every build job's clone token straight out of the queue. This registry raises the bar for
 * everyone who is <em>not</em> a cluster member, and the cluster password, transport security and the configured build
 * agent networks are what keep them out. See {@link BuildJobCloneTokenService} for the same boundary stated from the
 * token's side.
 * <p>
 * Every core node reconciles. The write is idempotent and taken under the per-key lock the map already offers, so
 * concurrent reconciliation is harmless and no leader election is needed.
 * <p>
 * Whether an agent can be constrained at all is decided <b>per agent</b> rather than globally, because it depends on the
 * agent. One that shares a JVM with a core node opens no client connection to the middleware and so has no observable
 * origin; the same holds for every agent under a provider that cannot report client addresses. Those are ordinary
 * topologies rather than attacks, and refusing them would break every single-node installation, so an agent with no
 * observed address is left unconstrained while one that is observed is held to the addresses it was observed at.
 *
 * @see BuildAgentNetworkPolicy
 */
@Service
@Profile(PROFILE_LOCALCI)
@Lazy(false)
public class BuildAgentAddressRegistryService {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentAddressRegistryService.class);

    /**
     * How often the observed addresses are reconciled into the distributed map. Short enough that an agent which
     * reconnects from a new address can clone again quickly, long enough not to matter next to the git traffic.
     */
    private static final long REFRESH_INTERVAL_MS = 30_000;

    /**
     * How long a request waits for a reconcile that another request is already performing. Generous relative to the
     * providers' own timeouts, so that reaching it means the middleware is not answering at all rather than merely
     * being busy - which is why the caller then refuses instead of falling back to the not-observable exemption.
     */
    private static final long REFRESH_ON_MISS_WAIT_MS = 5_000;

    private final DistributedDataAccessService distributedDataAccessService;

    private final BuildAgentNetworkPolicy buildAgentNetworkPolicy;

    /**
     * Local snapshot of the registered addresses, consulted per git request. Reading the distributed map on every
     * fetch would put a network round trip in front of every clone during an exam peak.
     * <p>
     * Volatile and replaced wholesale rather than mutated, so readers always see a complete generation.
     */
    private volatile Map<String, Set<String>> addressesByAgentName = Map.of();

    /**
     * Whether the middleware has ever answered a request for the connected clients on this node, an empty list included.
     * <p>
     * Not merely diagnostic. {@link #isRegisteredAddressOfAgent} uses it to tell a deployment that can never report
     * client addresses from one that normally can and has just failed, because the provider reports both as "no answer"
     * and only the former makes a missing entry mean "not observable". It stays false for the whole life of an
     * installation whose provider does not support the query, and flips to true on the first answer otherwise.
     * <p>
     * Deliberately not used as a global gate on the binding: whether an agent can be observed depends on the agent, not
     * on the node - an agent sharing a JVM with a core node opens no client connection even where the query works
     * perfectly - and gating globally on this once refused every clone on every single-node installation.
     */
    private volatile boolean addressObservationAvailable = false;

    /**
     * What the last logged observability state was, so the transition is announced once instead of every 30 seconds.
     * <p>
     * Null until the first reconcile round has concluded either way. This is the only user-visible signal that the
     * origin binding is not constraining anything on this installation - the per-request decision logs at debug, and a
     * deployment that expected the binding to be active would otherwise have no way to tell it is not.
     */
    private volatile Boolean loggedAddressObservability = null;

    /**
     * The agents this node has itself observed connecting, at any point since it started.
     * <p>
     * Needed because the middleware answers "who is connected" <b>per node</b>: Hazelcast's
     * {@code ClientService.getConnectedClients()} returns the clients of the member it is asked, not of the cluster. So
     * an agent absent from this node's answer has two meanings - it disconnected, or it simply never attached to this
     * particular member - and only the first may clear its addresses, because clearing them denies its clones on every
     * node. A node that has just joined has seen nobody yet and would otherwise blank the whole registry on its first
     * round.
     * <p>
     * Only ever added to. An agent that leaves and returns is re-observed, and the entry a node declines to clear stays
     * at the addresses that node last saw, which is the safe direction: the agent remains bound to real addresses
     * rather than becoming unbound.
     */
    private final Set<String> agentsObservedByThisNode = ConcurrentHashMap.newKeySet();

    /**
     * Serialises the refreshes triggered by a lookup miss, so that a request which does not perform the refresh waits
     * for the one in flight instead of deciding on the snapshot that refresh is about to replace.
     * <p>
     * A lock rather than an atomic timestamp because the requests that do not refresh still need the result. Debouncing
     * with a compare-and-set let the losers proceed immediately on the stale snapshot, and a stale snapshot with no
     * entry for an agent is indistinguishable from an agent whose origin cannot be observed - so a request could be
     * granted the not-observable exemption during the very refresh that was about to register the agent's real address.
     * Fair, so a burst of misses is served in arrival order rather than one thread starving behind the others.
     */
    private final ReentrantLock refreshOnMissLock = new ReentrantLock(true);

    /**
     * When the last reconcile performed for a decision completed, on {@link System#nanoTime}'s monotonic clock so that
     * a wall-clock adjustment cannot make a stale observation look recent.
     * <p>
     * Compared against the arrival of each request rather than against "how long ago": see {@link #reconcileForDecision}.
     * <p>
     * Initialised to the clock at construction rather than to a sentinel such as {@code Long.MIN_VALUE}. The comparison
     * there is an overflow-safe subtraction, which is only valid while the two values lie within a single nanoTime
     * period of each other; a sentinel breaks that and made the very first request read as "already observed", taking
     * the initial outcome below and refusing every build until the first scheduled reconcile. A timestamp from before
     * any request can arrive is both truthful - nothing has been observed for a decision yet - and safe to subtract.
     */
    private volatile long lastDecisionObservationAt = System.nanoTime();

    /**
     * What that reconcile established, so a request which waited for someone else's reconcile learns its outcome
     * instead of assuming it succeeded.
     */
    private volatile ObservationOutcome lastDecisionOutcome = ObservationOutcome.FAILED;

    public BuildAgentAddressRegistryService(DistributedDataAccessService distributedDataAccessService, BuildAgentNetworkPolicy buildAgentNetworkPolicy) {
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildAgentNetworkPolicy = buildAgentNetworkPolicy;
    }

    /**
     * Reconciles once this node is connected, and again whenever a build agent disconnects so that its addresses stop
     * authorizing clones immediately.
     * <p>
     * There is deliberately no connect-side listener: the middleware reports client disconnections but not connections
     * ({@code ClientListener.clientConnected} is not surfaced by the provider). An agent that reconnects is therefore
     * picked up either by the scheduled reconcile or, sooner, by the refresh that
     * {@link #isRegisteredAddressOfAgent} performs when it finds no matching address.
     */
    @PostConstruct
    public void registerListeners() {
        distributedDataAccessService.addConnectionStateListener(_ -> refreshRegisteredAddresses());
        distributedDataAccessService.addClientDisconnectionListener(_ -> refreshRegisteredAddresses());
    }

    /**
     * Reconciles the observed client addresses into the distributed map and refreshes the local snapshot.
     * <p>
     * Runs on every core node. An agent that is no longer connected loses its addresses, so a stale address cannot keep
     * authorizing clones after the agent it belonged to has gone, and its entry is kept until the agent can no longer
     * authenticate anything - see the reasoning at the removal loop below.
     */
    @Scheduled(initialDelay = 10_000, fixedDelay = REFRESH_INTERVAL_MS)
    public void refreshRegisteredAddresses() {
        reconcileObservedAddresses();
    }

    /**
     * What one reconcile round established. Three states rather than a boolean, because the two ways of ending without
     * a fresh snapshot mean opposite things for authorization: a provider that cannot report client addresses is a
     * supported deployment whose agents must stay unconstrained, whereas a round that simply failed establishes nothing
     * and must not be used to exempt anybody.
     */
    private enum ObservationOutcome {

        /** A provider answer was obtained and reconciled into the snapshot. */
        OBSERVED,

        /** The provider cannot report client addresses at all, so no agent's origin can be constrained on this node. */
        UNOBSERVABLE,

        /** This node is not in the cluster, or the round threw. Nothing was established either way. */
        FAILED
    }

    /**
     * Reconciles once and reports whether it managed to observe anything.
     * <p>
     * The distinction is load bearing for {@link #isRegisteredAddressOfAgent}: an agent with no entry may only be
     * granted the not-observable exemption on the strength of a reconcile that actually completed against a provider
     * answer. A round that returned early - this node not in the cluster, or the middleware unable to report client
     * addresses - proves nothing about the agent and must not be mistaken for one that found it absent.
     *
     * @return what this round established
     */
    private ObservationOutcome reconcileObservedAddresses() {
        if (!distributedDataAccessService.isConnectedToCluster()) {
            return ObservationOutcome.FAILED;
        }

        try {
            if (!distributedDataAccessService.clientsConnectDirectlyToCoreNodes()) {
                // The middleware can say where a client connected from, but not where it will clone from: its clients
                // connect to it rather than to a core node, so the two are different network paths and comparing them
                // would refuse every legitimate agent. Nothing is observed here - but the agents report the address a
                // core node measured for them, so the snapshot is still refreshed and an agent that has reported one is
                // still bound to it. Only agents that have reported nothing are unconstrained.
                logAddressObservability(false);
                refreshLocalSnapshot();
                return ObservationOutcome.UNOBSERVABLE;
            }

            // Empty means the middleware could not answer: an unsupported provider, or a query that failed or timed
            // out. Keep the previous snapshot in that case rather than concluding that every agent disconnected, which
            // would reject every clone in the cluster until the next successful round.
            Optional<Map<String, Set<String>>> observedAddresses = distributedDataAccessService.getConnectedClientAddresses();
            if (observedAddresses.isEmpty()) {
                log.debug("The middleware cannot report connected client addresses right now, keeping the previously registered ones");
                logAddressObservability(false);
                // Refreshed even so. Nothing is written, so the registered addresses of the last good round survive
                // untouched, and any agent that has reported one meanwhile is picked up rather than waiting for the
                // middleware to recover.
                refreshLocalSnapshot();
                return ObservationOutcome.UNOBSERVABLE;
            }
            Map<String, Set<String>> observed = toBuildAgentNames(observedAddresses.get());
            addressObservationAvailable = true;
            logAddressObservability(true);

            var registeredAddresses = distributedDataAccessService.getDistributedBuildAgentAddresses();
            ZonedDateTime observedAt = ZonedDateTime.now();

            agentsObservedByThisNode.addAll(observed.keySet());

            for (var entry : observed.entrySet()) {
                String agentName = entry.getKey();
                Set<String> addresses = Set.copyOf(entry.getValue());
                boolean withinAllowlist = addresses.stream().allMatch(buildAgentNetworkPolicy::isWithinAllowedRanges);

                registeredAddresses.lock(agentName);
                try {
                    BuildAgentAddressInfo previous = registeredAddresses.get(agentName);
                    registeredAddresses.put(agentName, new BuildAgentAddressInfo(agentName, addresses, observedAt, withinAllowlist));
                    if (previous == null || previous.withinAllowlist() != withinAllowlist) {
                        logAllowlistOutcome(agentName, addresses, withinAllowlist);
                    }
                }
                finally {
                    registeredAddresses.unlock(agentName);
                }
            }

            // The middleware answered, so an agent missing from the answer really is disconnected rather than merely
            // unobservable. That distinction is the whole reason the provider returns an Optional.
            //
            // Disconnected is not the same as gone, and deleting the entry outright would be a security hole rather
            // than mere cleanup: an absent entry means "origin not observable" to isRegisteredAddressOfAgent, which
            // then permits any address. The agent's identity in buildAgentInformation and its orphaned jobs in the
            // processing list are cleaned by different listeners, so between this removal and theirs a leaked key or
            // token would still name a known agent, still match a listed job, and no longer be bound to any address -
            // and if that cleanup fails, the window stays open. So an agent that can still be named or still holds jobs
            // keeps an entry with no addresses: known, observable, and matching nothing, which denies rather than
            // exempts. The entry is dropped only once the agent can no longer authenticate anything anyway.
            for (String registeredAgent : Set.copyOf(registeredAddresses.keySet())) {
                if (observed.containsKey(registeredAgent)) {
                    continue;
                }
                if (canStillAuthenticate(registeredAgent)) {
                    if (!agentsObservedByThisNode.contains(registeredAgent)) {
                        // This node has never seen that agent, so its absence here says nothing: the agent's client may
                        // simply be attached to other members. Clearing on that basis would deny its clones cluster
                        // wide, and a node that has just joined would do it to every agent at once.
                        log.debug("Build agent {} is not among this node's clients and never has been, so its registered addresses are left to the nodes that observe it",
                                registeredAgent);
                        continue;
                    }
                    registeredAddresses.lock(registeredAgent);
                    try {
                        BuildAgentAddressInfo previous = registeredAddresses.get(registeredAgent);
                        if (previous != null && !previous.addresses().isEmpty()) {
                            registeredAddresses.put(registeredAgent, new BuildAgentAddressInfo(registeredAgent, Set.of(), observedAt, previous.withinAllowlist()));
                            log.info("Build agent {} is no longer connected. Its addresses are cleared, and it may not clone until its registration and jobs are cleaned up.",
                                    registeredAgent);
                        }
                    }
                    finally {
                        registeredAddresses.unlock(registeredAgent);
                    }
                }
                else {
                    registeredAddresses.remove(registeredAgent);
                    log.debug("Removed network addresses of build agent {}, which is no longer connected and no longer registered", registeredAgent);
                }
            }

            refreshLocalSnapshot();
            return ObservationOutcome.OBSERVED;
        }
        catch (Exception e) {
            // Never let a reconciliation failure propagate into the scheduler: the previous snapshot stays in place
            // and the next run retries. Failing here must not stop builds.
            log.error("Could not refresh the registered build agent addresses", e);
            return ObservationOutcome.FAILED;
        }
    }

    /**
     * Announces whether this node can observe where build agents connect from, once per change rather than once per
     * round.
     *
     * @param observable whether the middleware answered the query for connected client addresses
     */
    private void logAddressObservability(boolean observable) {
        if (loggedAddressObservability != null && loggedAddressObservability == observable) {
            return;
        }
        loggedAddressObservability = observable;
        if (observable) {
            log.info("This node can observe where cluster clients connect from, so a build agent that connects as one may only clone from an address it is observed at. An agent "
                    + "that shares a JVM with a core node opens no client connection and is therefore not bound to any address, which is the normal single node topology.");
        }
        else {
            log.warn("Build agent clones are not bound to an agent's own address on this node: the distributed data provider either cannot report where its clients connect "
                    + "from, or its clients connect to the middleware rather than to a core node, in which case the address it observed is not the one a clone arrives from. "
                    + "The build agent networks and the per-build-job scoping still apply, and both are checked against the address of the request itself. An agent that "
                    + "shares a JVM with a core node opens no client connection at all and is likewise unbound, which is expected on a single node installation.");
        }
    }

    /**
     * Translates the middleware's view of who is connected into build agent short names, dropping every client that is
     * not a build agent.
     * <p>
     * The two providers name the same agent differently, and getting this wrong is silent: the registry would simply
     * never hold an entry for any agent, every agent would take the not-observable exemption, and the origin binding
     * would be off with nothing saying so.
     * <ul>
     * <li><b>Hazelcast</b> names a client by its instance name, which the agent sets to its own short name, so the
     * observed key already is the short name.</li>
     * <li><b>Redis</b> has no member/client split. A node's client name is its node identity, which is unique per node
     * and therefore cannot be the agent short name on a node that is both core node and build agent. The agent
     * publishes that identity as its {@code memberAddress}, so it is what maps the two together.</li>
     * </ul>
     * A client matching no build agent is left out rather than registered under its own name: it is another core node,
     * and an entry for it would only be a row in the admin view for something that never clones.
     * <p>
     * The {@code memberAddress} side is self-reported, so a cluster member could claim another agent's identity and
     * inherit its observed addresses. That is the boundary this registry already documents - such a member can read
     * every build job's clone token straight out of the queue - and it is the cluster password and the configured
     * build agent networks that keep non-members out.
     *
     * @param observedByClientName the middleware's client name to the addresses it is observed at
     * @return the same addresses keyed by build agent short name, without the clients that are not build agents
     */
    private Map<String, Set<String>> toBuildAgentNames(Map<String, Set<String>> observedByClientName) {
        var buildAgents = distributedDataAccessService.getDistributedBuildAgentInformation();
        Map<String, Set<String>> observedByAgentName = new HashMap<>();
        Map<String, String> agentNameByMemberAddress = null;

        for (var entry : observedByClientName.entrySet()) {
            String clientName = entry.getKey();
            if (buildAgents.get(clientName) != null) {
                // Hazelcast: the client name is the agent short name
                observedByAgentName.merge(clientName, new HashSet<>(entry.getValue()), (existing, added) -> {
                    existing.addAll(added);
                    return existing;
                });
                continue;
            }
            if (agentNameByMemberAddress == null) {
                // Built once per round and only when a client did not resolve directly, so the Hazelcast path never
                // pays for it
                agentNameByMemberAddress = new HashMap<>();
                for (var agent : buildAgents.values()) {
                    if (agent != null && agent.buildAgent() != null && agent.buildAgent().memberAddress() != null) {
                        agentNameByMemberAddress.put(agent.buildAgent().memberAddress(), agent.buildAgent().name());
                    }
                }
            }
            String agentName = agentNameByMemberAddress.get(clientName);
            if (agentName != null) {
                // Redis: the client name is the node identity the agent published as its member address
                observedByAgentName.merge(agentName, new HashSet<>(entry.getValue()), (existing, added) -> {
                    existing.addAll(added);
                    return existing;
                });
            }
        }
        return observedByAgentName;
    }

    private void logAllowlistOutcome(String agentName, Set<String> addresses, boolean withinAllowlist) {
        if (withinAllowlist) {
            log.info("Build agent {} is connected from {}", agentName, addresses);
        }
        else {
            log.warn("Build agent {} is connected from {}, which is outside the configured build agent networks {}. It will not be allowed to clone repositories.", agentName,
                    addresses, buildAgentNetworkPolicy.getAllowedRanges());
        }
    }

    /**
     * Rebuilds the snapshot the git paths are answered from.
     * <p>
     * Every observed agent enters it, including one connected from outside the allowlist. Filtering those out here
     * looks like defence in depth and is the opposite: {@link #isRegisteredAddressOfAgent} reads the absence of an
     * entry as "this agent's origin cannot be observed" and permits any address, so excluding an agent would turn the
     * one flagged as suspicious into the only one with no origin binding at all - its credential would then be usable
     * from any address that happens to sit inside the allowed ranges. The allowlist is applied to the address of the
     * request instead, by {@link BuildAgentNetworkPolicy#isWithinAllowedRanges} at each call site, which is where it
     * constrains rather than exempts. {@code withinAllowlist} stays on the entry for the admin UI and the startup log.
     */
    private void refreshLocalSnapshot() {
        Map<String, Set<String>> snapshot = new HashMap<>();
        // The union of both sources. An agent is bound to every address either one knows about, which is what makes the
        // two complementary rather than competing: where a core node can observe the agent it does, where it cannot the
        // agent's own measurement stands in, and where both exist and disagree - a load balancer on the git path but
        // not on the cluster path - the agent is authorized from either, which is correct because it really does reach
        // core nodes from both.
        addAddresses(snapshot, distributedDataAccessService.getBuildAgentAddressMap());
        addAddresses(snapshot, distributedDataAccessService.getBuildAgentReportedAddressMap());
        addressesByAgentName = Map.copyOf(snapshot);
    }

    /**
     * Merges one source of addresses into the snapshot under construction.
     *
     * @param snapshot the snapshot being built
     * @param source   agent short name to the addresses that source knows about
     */
    private static void addAddresses(Map<String, Set<String>> snapshot, Map<String, BuildAgentAddressInfo> source) {
        for (var entry : source.entrySet()) {
            BuildAgentAddressInfo info = entry.getValue();
            if (info != null) {
                // An entry with no addresses is a tombstone for a disconnected agent and has to keep its key: presence
                // with nothing in it denies, whereas absence would grant the not-observable exemption.
                snapshot.computeIfAbsent(entry.getKey(), _ -> new HashSet<>()).addAll(info.addresses());
            }
        }
    }

    /**
     * Checks whether a git request may be treated as coming from the named build agent.
     * <p>
     * Answered from the local snapshot, so this costs a map lookup and no network traffic.
     *
     * @param agentName the build agent short name the request claims to come from
     * @param ipAddress the address the request actually came from, resolved without trusting client-set headers
     * @return whether that agent may be treated as calling from that address: {@code true} when it is observed there,
     *         and also {@code true} when no address is known for it at all, since an agent whose cluster connection
     *         cannot be observed - one co-located with a core node, or any agent under a provider that cannot report
     *         client addresses - has no origin to check and must not be refused for it
     */
    public boolean isRegisteredAddressOfAgent(String agentName, String ipAddress) {
        if (agentName == null || ipAddress == null) {
            return false;
        }
        // Stamped before anything that can block, because this is what the reconcile below has to be newer than.
        long arrivedAt = System.nanoTime();
        if (matchesRegisteredAddress(agentName, ipAddress)) {
            return true;
        }

        // An agent that reconnects re-registers itself and starts pulling jobs immediately, while its observed address
        // is only picked up by the next scheduled reconcile. Without this, every agent restart would fail builds for up
        // to the refresh interval, and fail them in a way that looks like a configuration problem. Refreshing on a miss
        // closes that window; the coordination inside bounds how much work a wrong address can cause.
        log.debug("No registered address matches {} for build agent {}, reconciling the registry before deciding", ipAddress, agentName);
        ObservationOutcome outcome = reconcileForDecision(arrivedAt);
        if (outcome == ObservationOutcome.FAILED) {
            // Nothing was established, so there is nothing to grant an exemption on the strength of. Denying costs
            // nothing extra here: reaching this means the middleware is not answering, and the token and job-scope
            // checks that follow read the processing list from that same middleware, so the request was going to fail
            // anyway. Deliberately not the same as UNOBSERVABLE, handled below, which can be a working deployment.
            log.warn("Could not reconcile the build agent addresses while deciding whether {} may act as build agent {}, so the request is refused", ipAddress, agentName);
            return false;
        }
        if (matchesRegisteredAddress(agentName, ipAddress)) {
            return true;
        }

        // Everything below turns on whether "this agent has no entry" is informative. It is what grants the exemption,
        // so it may only do so when a reconcile could actually have entered the agent and did not.
        //
        // The exemption itself is not optional. An agent with no entry is normally one whose origin this node cannot
        // observe - one sharing a JVM with a core node opens no client connection to the middleware at all - and refusing
        // those would break every single-node installation. An agent that *is* observed is held to the addresses it was
        // observed at.
        boolean absenceIsInformative;
        if (outcome == ObservationOutcome.OBSERVED) {
            // A reconcile newer than this request answered, so absence means the provider did not report this agent.
            absenceIsInformative = true;
        }
        else {
            // The provider returned no answer at all, and that has two meanings which must not be conflated: a
            // deployment that can never report client addresses, and one that normally can but just failed or timed out.
            // Only the first makes absence informative. Told apart by whether this node has ever obtained an answer,
            // because a deployment where the query works will have answered at least once since startup - so a first-ever
            // failure is treated as the permanent case, which is the safe direction for availability, and every
            // subsequent one as transient.
            absenceIsInformative = !addressObservationAvailable;
            if (!absenceIsInformative) {
                // The preserved snapshot from the last good reconcile is still enforced above, which is the point: a
                // provider hiccup must not lift an origin binding that has already been established for this agent.
                log.warn("Client addresses could not be observed while deciding whether {} may act as build agent {}. The addresses from the last successful observation still "
                        + "apply, and an agent that is not among them is refused.", ipAddress, agentName);
            }
        }

        if (!hasRegisteredAddresses(agentName) && absenceIsInformative) {
            log.debug("Build agent {} has no observed cluster connection, so its origin cannot be constrained", agentName);
            return true;
        }
        return false;
    }

    /**
     * Brings the snapshot up to date for a decision that missed, coordinating with any reconcile already in flight.
     * <p>
     * The condition is that a reconcile <b>completed after this request arrived</b>, not that one completed recently.
     * "Recently" cannot speak for a particular agent: there is no connect-side callback, so an agent may connect and
     * publish its identity immediately after a reconcile finishes, and a request using its credential would then find no
     * entry and be handed the not-observable exemption on the strength of an observation taken before the agent existed.
     * Arrival ordering rules that out, because in both the legitimate and the stolen-credential case the agent connected
     * before the request could be made: a reconcile that finishes after the request arrived therefore also saw whatever
     * connection the agent had.
     * <p>
     * That criterion coalesces rather than multiplies work. A burst of requests all arriving before one reconcile
     * completes is satisfied by that single reconcile, and the fair lock keeps at most one running, so the cost is
     * bounded by reconcile duration rather than by request count. It has no minimum interval on purpose: an interval
     * would mean answering some requests from an observation older than they are, which is the defect above. What limits
     * who can trigger this at all is upstream - a request only reaches here after the caller has established that the
     * name belongs to a registered build agent, by a single-key lookup over https or by a key match over ssh.
     *
     * @param arrivedAt when the deciding request arrived, on {@link System#nanoTime}'s clock
     * @return what the reconcile backing this decision established, whether this call performed it or waited for it
     */
    private ObservationOutcome reconcileForDecision(long arrivedAt) {
        try {
            if (!refreshOnMissLock.tryLock(REFRESH_ON_MISS_WAIT_MS, TimeUnit.MILLISECONDS)) {
                return ObservationOutcome.FAILED;
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ObservationOutcome.FAILED;
        }
        try {
            if (lastDecisionObservationAt - arrivedAt > 0) {
                // Subtraction rather than a plain comparison, so this stays correct across a nanoTime overflow. A
                // reconcile finished while this request was waiting for the lock, so its observation covers this
                // request and repeating it would establish nothing new.
                return lastDecisionOutcome;
            }
            ObservationOutcome outcome = reconcileObservedAddresses();
            lastDecisionOutcome = outcome;
            lastDecisionObservationAt = System.nanoTime();
            return outcome;
        }
        finally {
            refreshOnMissLock.unlock();
        }
    }

    /**
     * Decides whether an agent that is no longer connected could still authenticate a git request, and therefore still
     * needs its origin constrained.
     *
     * @param agentName the build agent that disappeared from the observed connections
     * @return whether the agent can still be named as a build agent or still holds a job whose token would match
     */
    private boolean canStillAuthenticate(String agentName) {
        if (distributedDataAccessService.getDistributedBuildAgentInformation().get(agentName) != null) {
            return true;
        }
        return !distributedDataAccessService.getProcessingJobsForAgentByName(agentName).isEmpty();
    }

    /**
     * @param agentName the build agent to look up
     * @return whether this node has an entry for that agent, whether or not it currently holds addresses. An entry with
     *         no addresses is a disconnected agent that has not been cleaned up yet, and it must deny rather than fall
     *         through to the not-observable exemption, so presence is the question here and not emptiness.
     */
    private boolean hasRegisteredAddresses(String agentName) {
        return addressesByAgentName.containsKey(agentName);
    }

    private boolean matchesRegisteredAddress(String agentName, String ipAddress) {
        Set<String> addresses = addressesByAgentName.get(agentName);
        if (addresses == null) {
            return false;
        }
        if (addresses.contains(ipAddress)) {
            return true;
        }
        // Compare the parsed addresses too, because the two sides are formatted independently: the middleware reports
        // whatever InetAddress or Redis produced, while the request side comes from the servlet container. The same
        // address can therefore appear as ::1 and 0:0:0:0:0:0:0:1, or as ::ffff:10.0.0.5 and 10.0.0.5. The second of
        // those needs a conversion that neither string equality nor IPAddress.equals performs, which is why this goes
        // through IpAddresses rather than comparing parsed values directly.
        return addresses.stream().anyMatch(address -> IpAddresses.sameHost(address, ipAddress));
    }

    /**
     * Checks whether an address belongs to some build agent this cluster has observed connecting from it.
     * <p>
     * This is the automatic counterpart of listing an address in {@code artemis.rate-limiting.exempt-addresses}: build
     * agents drive far more git traffic from one address than any person does - several concurrent jobs, each cloning
     * an assignment, test, solution and auxiliary repository - so a per-address limit sized for people would throttle
     * them, and the operator would have to maintain a static list of agent addresses by hand and keep it correct as
     * agents move. Registering happens when the agent connects, so the exemption follows the agents by itself.
     * <p>
     * Deliberately answered from the local snapshot with no reconcile: this decides whether to <em>skip</em> a rate
     * limit, so a miss costs a limited request rather than a refused one, and it must stay cheap enough to run ahead of
     * the limiter it guards.
     * <p>
     * Unlike {@link #isRegisteredAddressOfAgent} this does not ask <b>which</b> agent, because it grants nothing: it
     * decides only whether to count a request against a quota. Authorization always names the agent.
     *
     * @param ipAddress the address a request came from, resolved without trusting client-set headers
     * @return whether any build agent is currently registered at that address
     */
    public boolean isRegisteredBuildAgentAddress(@Nullable String ipAddress) {
        if (ipAddress == null) {
            return false;
        }
        for (Set<String> addresses : addressesByAgentName.values()) {
            if (addresses.contains(ipAddress) || addresses.stream().anyMatch(address -> IpAddresses.sameHost(address, ipAddress))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether the middleware has reported any client connection on this node. Diagnostics only; see the field.
     */
    public boolean isAddressObservationAvailable() {
        return addressObservationAvailable;
    }
}
