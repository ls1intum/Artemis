package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentAddressInfo;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

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
     * When a lookup miss last triggered an out-of-band refresh, used to debounce them.
     * <p>
     * Atomic rather than volatile because the check and the update have to be one step: many clone requests arrive
     * concurrently during an exam peak, and a read-then-write would let all of them pass the interval check and each
     * start its own refresh against the middleware.
     */
    private final AtomicLong lastRefreshOnMissAt = new AtomicLong(0);

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
     * Runs on every core node. Entries for agents that are no longer connected are removed, so a stale address cannot
     * keep authorizing clones after the agent it belonged to has gone.
     */
    @Scheduled(initialDelay = 10_000, fixedDelay = REFRESH_INTERVAL_MS)
    public void refreshRegisteredAddresses() {
        if (!distributedDataAccessService.isConnectedToCluster()) {
            return;
        }

        try {
            // Empty means the middleware could not answer: an unsupported provider, or a query that failed or timed
            // out. Keep the previous snapshot in that case rather than concluding that every agent disconnected, which
            // would reject every clone in the cluster until the next successful round.
            Optional<Map<String, Set<String>>> observedAddresses = distributedDataAccessService.getConnectedClientAddresses();
            if (observedAddresses.isEmpty()) {
                log.debug("The middleware cannot report connected client addresses right now, keeping the previously registered ones");
                return;
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

            // Safe to remove now: the middleware answered, so an agent missing from the answer really is disconnected
            // rather than merely unobservable. That distinction is the whole reason the provider returns an Optional.
            for (String registeredAgent : Set.copyOf(registeredAddresses.keySet())) {
                if (!observed.containsKey(registeredAgent)) {
                    registeredAddresses.remove(registeredAgent);
                    log.debug("Removed network addresses of build agent {}, which is no longer connected", registeredAgent);
                }
            }

            refreshLocalSnapshot();
        }
        catch (Exception e) {
            // Never let a reconciliation failure propagate into the scheduler: the previous snapshot stays in place
            // and the next run retries. Failing here must not stop builds.
            log.error("Could not refresh the registered build agent addresses", e);
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

    private void refreshLocalSnapshot() {
        Map<String, Set<String>> snapshot = new HashMap<>();
        for (var entry : distributedDataAccessService.getBuildAgentAddressMap().entrySet()) {
            BuildAgentAddressInfo info = entry.getValue();
            if (info != null && info.withinAllowlist()) {
                // Only agents inside the allowlist enter the snapshot, so the git paths need a single lookup rather
                // than a lookup plus a separate allowlist decision.
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
        // closes that window; the debounce keeps a wrong address from turning into a reconcile per request.
        long now = System.currentTimeMillis();
        long previousRefresh = lastRefreshOnMissAt.get();
        // Only the request that wins the compareAndSet refreshes; the others fall straight through to the decision
        // below, which is what they would have reached anyway had the refresh found nothing for them.
        if (now - previousRefresh > MIN_REFRESH_ON_MISS_INTERVAL_MS && lastRefreshOnMissAt.compareAndSet(previousRefresh, now)) {
            log.debug("No registered address matches {} for build agent {}, refreshing the registry once before deciding", ipAddress, agentName);
            refreshRegisteredAddresses();
            if (matchesRegisteredAddress(agentName, ipAddress)) {
                return true;
            }
        }

        // The decision is per agent, not global: an agent with no entry at all is one whose origin this node cannot
        // observe, and that is a legitimate topology rather than an attack. A build agent co-located with the core node
        // in a single JVM never opens a client connection to the middleware, so there is nothing to observe for it, and
        // the same is true of a provider that cannot report client addresses. Refusing those would break every
        // single-node installation. An agent that *is* observed is held to the addresses it was observed at.
        if (!hasRegisteredAddresses(agentName)) {
            log.debug("Build agent {} has no observed cluster connection, so its origin cannot be constrained", agentName);
            return true;
        }
        return false;
    }

    /**
     * @param agentName the build agent to look up
     * @return whether this node knows any address for that agent, i.e. whether its origin can be constrained at all
     */
    private boolean hasRegisteredAddresses(String agentName) {
        Set<String> addresses = addressesByAgentName.get(agentName);
        return addresses != null && !addresses.isEmpty();
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
        // address can therefore appear as ::1 and 0:0:0:0:0:0:0:1, or as ::ffff:10.0.0.5 and 10.0.0.5.
        IPAddress requested = new IPAddressString(ipAddress).getAddress();
        if (requested == null) {
            return false;
        }
        return addresses.stream().map(address -> new IPAddressString(address).getAddress()).filter(Objects::nonNull).anyMatch(requested::equals);
    }

    /**
     * @return whether the middleware has reported any client connection on this node. Diagnostics only; see the field.
     */
    public boolean isAddressObservationAvailable() {
        return addressObservationAvailable;
    }
}
