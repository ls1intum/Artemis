package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentAddressInfo;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;

/**
 * Records which network addresses each build agent connects to the cluster from, so that the git authorization paths
 * can require a build agent's clone to come from an address that agent is actually connected from.
 * <p>
 * The addresses are observed by the middleware on the agent's own cluster connection, never reported by the agent.
 * That is the whole point: {@code BuildAgentDTO.memberAddress} is the agent's view of its local socket, which is
 * pre-NAT and which a hostile agent can set to anything, whereas an observed address is where the connection came
 * from. Writing the observation into a distributed map lets any core node authorize a git request from its own local
 * snapshot, without a provider-specific call on the hot path and without caring which node the agent is attached to.
 * <p>
 * Every core node reconciles. The write is idempotent and taken under the per-key lock the map already offers, so
 * concurrent reconciliation is harmless and no leader election is needed.
 * <p>
 * Providers that cannot observe client connections return nothing, which is recorded as "unknown" rather than as
 * "no agent is connected". Denying every build because the middleware cannot answer would be a far worse failure than
 * not applying the binding, so the binding is skipped and the situation is logged loudly at startup.
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
     * Whether the provider was able to observe any client connection since startup. Distinguishes "this provider
     * cannot tell us" from "no build agent is connected", which look identical in the data.
     */
    private volatile boolean addressObservationAvailable = false;

    public BuildAgentAddressRegistryService(DistributedDataAccessService distributedDataAccessService, BuildAgentNetworkPolicy buildAgentNetworkPolicy) {
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildAgentNetworkPolicy = buildAgentNetworkPolicy;
    }

    /**
     * Reconciles once at startup and subscribes to client connection changes, so an agent that connects between two
     * scheduled runs is registered immediately rather than after up to {@value #REFRESH_INTERVAL_MS} milliseconds.
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
            Map<String, Set<String>> observed = distributedDataAccessService.getConnectedClientAddresses();
            if (!observed.isEmpty()) {
                addressObservationAvailable = true;
            }

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

            // Drop agents that are no longer connected. Only done when the provider could observe anything at all,
            // so a provider that reports nothing does not wipe a registry another node is maintaining.
            if (addressObservationAvailable) {
                for (String registeredAgent : Set.copyOf(registeredAddresses.keySet())) {
                    if (!observed.containsKey(registeredAgent)) {
                        registeredAddresses.remove(registeredAgent);
                        log.debug("Removed network addresses of build agent {}, which is no longer connected", registeredAgent);
                    }
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
     * @return whether that agent is currently connected from that address. Also {@code true} when the middleware
     *         cannot observe client addresses at all, because a provider that cannot answer must not deny every build
     */
    public boolean isRegisteredAddressOfAgent(String agentName, String ipAddress) {
        if (!addressObservationAvailable) {
            return true;
        }
        if (agentName == null || ipAddress == null) {
            return false;
        }
        Set<String> addresses = addressesByAgentName.get(agentName);
        return addresses != null && addresses.contains(ipAddress);
    }

    /**
     * @return whether the middleware can observe client addresses at all. When it cannot,
     *         {@link #isRegisteredAddressOfAgent} does not constrain anything and the deployment should be told so.
     */
    public boolean isAddressObservationAvailable() {
        return addressObservationAvailable;
    }
}
