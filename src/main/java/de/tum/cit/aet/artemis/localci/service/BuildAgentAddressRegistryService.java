package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.annotation.PostConstruct;

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
     * Shortest gap between two refreshes triggered by a lookup miss. Bounds the work a caller presenting an unknown
     * address can cause, while still closing the window after an agent reconnects.
     */
    private static final long MIN_REFRESH_ON_MISS_INTERVAL_MS = 2_000;

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
     * Whether the middleware has ever reported a client connection on this node. Diagnostics only: the authorization
     * decision is made per agent in {@link #isRegisteredAddressOfAgent}, because whether an agent can be observed
     * depends on the agent, not on the node. A build agent sharing a JVM with a core node opens no client connection at
     * all, so on a single-node installation this stays false while everything works normally.
     */
    private volatile boolean addressObservationAvailable = false;

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
     * When a reconcile triggered by a lookup miss last <em>completed</em>, used to debounce them. Measured from
     * completion rather than from the start, so a request that waited for a refresh does not immediately start another.
     */
    private volatile long lastRefreshOnMissCompletedAt = 0;

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
            // Empty means the middleware could not answer: an unsupported provider, or a query that failed or timed
            // out. Keep the previous snapshot in that case rather than concluding that every agent disconnected, which
            // would reject every clone in the cluster until the next successful round.
            Optional<Map<String, Set<String>>> observedAddresses = distributedDataAccessService.getConnectedClientAddresses();
            if (observedAddresses.isEmpty()) {
                log.debug("The middleware cannot report connected client addresses right now, keeping the previously registered ones");
                return ObservationOutcome.UNOBSERVABLE;
            }
            Map<String, Set<String>> observed = observedAddresses.get();
            addressObservationAvailable = true;

            var registeredAddresses = distributedDataAccessService.getDistributedBuildAgentAddresses();
            ZonedDateTime observedAt = ZonedDateTime.now();

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
        for (var entry : distributedDataAccessService.getBuildAgentAddressMap().entrySet()) {
            BuildAgentAddressInfo info = entry.getValue();
            if (info != null) {
                snapshot.put(entry.getKey(), new HashSet<>(info.addresses()));
            }
        }
        addressesByAgentName = Map.copyOf(snapshot);
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
        if (matchesRegisteredAddress(agentName, ipAddress)) {
            return true;
        }

        // An agent that reconnects re-registers itself and starts pulling jobs immediately, while its observed address
        // is only picked up by the next scheduled reconcile. Without this, every agent restart would fail builds for up
        // to the refresh interval, and fail them in a way that looks like a configuration problem. Refreshing on a miss
        // closes that window; the coordination inside bounds how much work a wrong address can cause.
        log.debug("No registered address matches {} for build agent {}, reconciling the registry before deciding", ipAddress, agentName);
        ObservationOutcome outcome = refreshedOnMiss();
        if (outcome == ObservationOutcome.FAILED) {
            // Nothing was established, so there is nothing to grant an exemption on the strength of. Denying costs
            // nothing extra here: reaching this means the middleware is not answering, and the token and job-scope
            // checks that follow read the processing list from that same middleware, so the request was going to fail
            // anyway. This is deliberately not the same as UNOBSERVABLE below, which is a working deployment.
            log.warn("Could not reconcile the build agent addresses while deciding whether {} may act as build agent {}, so the request is refused", ipAddress, agentName);
            return false;
        }
        if (outcome == ObservationOutcome.UNOBSERVABLE) {
            // The provider cannot report client addresses on this node - a Redis deployment without CLIENT LIST access,
            // or a Hazelcast client - so no agent's origin is observable and the binding cannot apply to anyone. Denying
            // here would refuse every build on such a deployment, which is why the provider distinguishes "cannot
            // answer" from "nobody is connected" in the first place.
            log.debug("Client addresses cannot be observed on this node, so the origin of build agent {} cannot be constrained", agentName);
            return true;
        }
        if (matchesRegisteredAddress(agentName, ipAddress)) {
            return true;
        }

        // The decision is per agent, not global: an agent with no entry at all is one whose origin this node cannot
        // observe, and that is a legitimate topology rather than an attack. A build agent co-located with the core node
        // in a single JVM never opens a client connection to the middleware, so there is nothing to observe for it, and
        // the same is true of a provider that cannot report client addresses. Refusing those would break every
        // single-node installation. An agent that *is* observed is held to the addresses it was observed at.
        //
        // Reached only after a reconcile completed, which is what makes "no entry" mean "not observable" rather than
        // "not looked at yet". An agent that has just published itself and is about to be registered would otherwise be
        // granted this exemption for as long as the snapshot lagged.
        if (!hasRegisteredAddresses(agentName)) {
            log.debug("Build agent {} has no observed cluster connection, so its origin cannot be constrained", agentName);
            return true;
        }
        return false;
    }

    /**
     * Brings the snapshot up to date for a decision that missed, coordinating with any refresh already in flight.
     * <p>
     * Every caller either performs the reconcile or waits for the one in progress, and only then decides. Letting the
     * waiters proceed immediately - which a compare-and-set debounce does - means deciding on the snapshot that the
     * in-flight reconcile is about to replace, and since a missing entry is what grants the not-observable exemption,
     * a caller could be exempted during the very refresh that was about to register the agent's address. Two concurrent
     * requests with a leaked credential were enough: one refreshes, the other is admitted.
     * <p>
     * The debounce is kept for the work it bounds, but measured from the completion of the last reconcile, so a waiter
     * that has just been handed a fresh snapshot does not start another round on top of it.
     *
     * @return what backs the decision now: this call's own reconcile, or the one it waited for
     */
    private ObservationOutcome refreshedOnMiss() {
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
            if (System.currentTimeMillis() - lastRefreshOnMissCompletedAt <= MIN_REFRESH_ON_MISS_INTERVAL_MS) {
                // A reconcile completed while this request was waiting for the lock, or moments before it arrived. The
                // snapshot is already as fresh as one more round would make it.
                return ObservationOutcome.OBSERVED;
            }
            ObservationOutcome outcome = reconcileObservedAddresses();
            if (outcome == ObservationOutcome.OBSERVED) {
                lastRefreshOnMissCompletedAt = System.currentTimeMillis();
            }
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
     * @return whether the middleware has reported any client connection on this node. Diagnostics only; see the field.
     */
    public boolean isAddressObservationAvailable() {
        return addressObservationAvailable;
    }
}
