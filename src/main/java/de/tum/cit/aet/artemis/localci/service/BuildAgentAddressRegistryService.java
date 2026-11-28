package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.IpAddresses;

/**
 * Caches the addresses that agents measure over the git path. A reported address binds that agent's git requests to
 * its measured origin; agents that have not reported yet can still start their first build.
 */
@Service
@Profile(PROFILE_LOCALCI)
@Lazy(false)
public class BuildAgentAddressRegistryService {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentAddressRegistryService.class);

    /**
     * How often the local snapshot is refreshed from the reported addresses. Short enough that an agent which
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

    private final AtomicReference<Map<String, Set<String>>> addressesByAgentName = new AtomicReference<>(Map.of());

    // Requests missing an address in their snapshot share a refresh that completed after they arrived.
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
    private volatile boolean lastDecisionRefreshSucceeded;

    public BuildAgentAddressRegistryService(DistributedDataAccessService distributedDataAccessService) {
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /** Refreshes the snapshot when cluster connectivity changes. */
    @PostConstruct
    public void registerListeners() {
        refreshRegisteredAddresses();
        distributedDataAccessService.addClientDisconnectionListener(_ -> refreshRegisteredAddresses());
    }

    /** Refreshes the local snapshot from the expiring address reports. */
    @Scheduled(initialDelay = 10_000, fixedDelay = REFRESH_INTERVAL_MS)
    public void refreshRegisteredAddresses() {
        refreshOnMissLock.lock();
        try {
            refreshSnapshot();
        }
        finally {
            refreshOnMissLock.unlock();
        }
    }

    private boolean refreshSnapshot() {
        if (!distributedDataAccessService.isConnectedToCluster()) {
            return false;
        }
        try {
            Map<String, Set<String>> snapshot = new HashMap<>();
            distributedDataAccessService.getBuildAgentReportedAddressMap().forEach((name, info) -> snapshot.put(name, Set.copyOf(info.addresses())));
            addressesByAgentName.set(Map.copyOf(snapshot));
            return true;
        }
        catch (RuntimeException e) {
            log.warn("Could not refresh the registered build agent addresses", e);
            return false;
        }
    }

    /**
     * Checks whether a git request may be treated as coming from the named build agent.
     * <p>
     * Answered from the local snapshot, so this costs a map lookup and no network traffic.
     *
     * @param agentName the build agent short name the request claims to come from
     * @param ipAddress the address the request actually came from, resolved without trusting client-set headers
     * @return whether the address matches or a successful refresh confirms the agent has no report yet
     */
    public boolean isRegisteredAddressOfAgent(String agentName, String ipAddress) {
        if (agentName == null || ipAddress == null) {
            return false;
        }
        // Stamped before anything that can block, because this is what the reconcile below has to be newer than.
        long arrivedAt = System.nanoTime();
        if (matchesAddress(addressesByAgentName.get().get(agentName), ipAddress)) {
            return true;
        }

        if (!reconcileForDecision(arrivedAt)) {
            return false;
        }
        Set<String> addresses = addressesByAgentName.get().get(agentName);
        return addresses == null || matchesAddress(addresses, ipAddress);
    }

    /**
     * Brings the snapshot up to date for a decision that missed, coordinating with any reconcile already in flight.
     * A caller may use a concurrent refresh only if it completed after that caller arrived.
     *
     * @param arrivedAt when the deciding request arrived, on {@link System#nanoTime}'s clock
     * @return what the reconcile backing this decision established, whether this call performed it or waited for it
     */
    private boolean reconcileForDecision(long arrivedAt) {
        try {
            if (!refreshOnMissLock.tryLock(REFRESH_ON_MISS_WAIT_MS, TimeUnit.MILLISECONDS)) {
                return false;
            }
        }
        catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            if (lastDecisionObservationAt - arrivedAt > 0) {
                // Subtraction rather than a plain comparison, so this stays correct across a nanoTime overflow. A
                // reconcile finished while this request was waiting for the lock, so its observation covers this
                // request and repeating it would establish nothing new.
                return lastDecisionRefreshSucceeded;
            }
            lastDecisionRefreshSucceeded = refreshSnapshot();
            lastDecisionObservationAt = System.nanoTime();
            return lastDecisionRefreshSucceeded;
        }
        finally {
            refreshOnMissLock.unlock();
        }
    }

    private static boolean matchesAddress(@Nullable Set<String> addresses, String ipAddress) {
        if (addresses == null) {
            return false;
        }
        if (addresses.contains(ipAddress)) {
            return true;
        }
        // Agents and servlet containers can represent the same IP address differently.
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
        return addressesByAgentName.get().values().stream().anyMatch(addresses -> matchesAddress(addresses, ipAddress));
    }

}
