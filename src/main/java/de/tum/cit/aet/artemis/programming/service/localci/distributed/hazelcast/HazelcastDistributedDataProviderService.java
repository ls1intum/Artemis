package de.tum.cit.aet.artemis.programming.service.localci.distributed.hazelcast;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.core.LifecycleListener;

import de.tum.cit.aet.artemis.core.config.LocalCIBuildAgentHazelcastDataCondition;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

@Lazy
@Service
@Conditional(LocalCIBuildAgentHazelcastDataCondition.class)
public class HazelcastDistributedDataProviderService implements DistributedDataProvider {

    private static final Logger log = LoggerFactory.getLogger(HazelcastDistributedDataProviderService.class);

    private final HazelcastInstance hazelcastInstance;

    /**
     * Registered connection state listeners. The callback receives true for initial connection,
     * false for reconnection after disconnection.
     */
    private final Map<UUID, Consumer<Boolean>> connectionStateListeners = new ConcurrentHashMap<>();

    /**
     * Tracks whether a successful connection has been established at least once.
     * Used to distinguish initial connection from reconnection in lifecycle events.
     */
    private final AtomicBoolean hasConnectedBefore = new AtomicBoolean(false);

    /**
     * The Hazelcast lifecycle listener registration ID, used for cleanup.
     */
    private UUID hazelcastLifecycleListenerId;

    public HazelcastDistributedDataProviderService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        registerHazelcastLifecycleListener();
    }

    /**
     * Registers a Hazelcast lifecycle listener to track connection state changes.
     * For clients, this detects CLIENT_CONNECTED and CLIENT_DISCONNECTED events.
     * For cluster members, this detects STARTED events.
     */
    private void registerHazelcastLifecycleListener() {
        LifecycleListener listener = event -> {
            log.debug("Hazelcast lifecycle event: {}", event.getState());

            switch (event.getState()) {
                case CLIENT_CONNECTED -> {
                    // Client has (re)connected to the cluster
                    boolean isInitialConnection = !hasConnectedBefore.getAndSet(true);
                    log.info("Hazelcast client connected to cluster (initial={})", isInitialConnection);
                    notifyConnectionStateListeners(isInitialConnection);
                }
                case CLIENT_DISCONNECTED -> {
                    // Client lost connection - listeners will need to re-register on reconnection
                    log.warn("Hazelcast client disconnected from cluster");
                }
                case STARTED -> {
                    // For cluster members, STARTED indicates the instance is ready
                    if (!(hazelcastInstance instanceof HazelcastClientProxy)) {
                        boolean isInitialConnection = !hasConnectedBefore.getAndSet(true);
                        log.info("Hazelcast cluster member started (initial={})", isInitialConnection);
                        notifyConnectionStateListeners(isInitialConnection);
                    }
                }
                default -> {
                    // Other events (STARTING, SHUTTING_DOWN, SHUTDOWN, etc.) are logged but not acted upon
                }
            }
        };

        hazelcastLifecycleListenerId = hazelcastInstance.getLifecycleService().addLifecycleListener(listener);
    }

    /**
     * Notifies all registered connection state listeners about a connection event.
     *
     * @param isInitialConnection true if this is the first connection, false if reconnecting
     */
    private void notifyConnectionStateListeners(boolean isInitialConnection) {
        for (var entry : connectionStateListeners.entrySet()) {
            try {
                entry.getValue().accept(isInitialConnection);
            }
            catch (Exception e) {
                log.error("Error notifying connection state listener {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name) {
        return new HazelcastDistributedQueue<>(hazelcastInstance.getQueue(name));
    }

    @Override
    public <T extends Comparable<T>> DistributedQueue<T> getPriorityQueue(String name) {
        return getQueue(name);
    }

    @Override
    public <K, V> DistributedMap<K, V> getMap(String name) {
        return new HazelcastDistributedMap<>(hazelcastInstance.getMap(name));
    }

    @Override
    public <T> DistributedTopic<T> getTopic(String name) {
        return new HazelcastDistributedTopic<>(hazelcastInstance.getTopic(name));
    }

    /**
     * Checks if the Hazelcast instance is active and operational.
     *
     * @return {@code true} if the Hazelcast instance has been initialized and is actively running,
     *         {@code false} if the instance has not been initialized or is no longer running
     */
    @Override
    public boolean isInstanceRunning() {
        try {
            return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
        }
        catch (HazelcastInstanceNotActiveException e) {
            return false;
        }

    }

    /**
     * Returns a unique identifier for the local Hazelcast instance.
     * For cluster members, returns the member's cluster address in normalized format.
     * For clients (e.g., build agents in client mode), returns the client's local endpoint address,
     * or a unique fallback identifier if not yet connected.
     *
     * <p>
     * The address format is always {@code [host]:port} where the host is the IP address or hostname.
     * IPv6 addresses are wrapped in brackets per RFC 3986, and IPv4 addresses are also
     * wrapped in brackets for consistency across the codebase. Note that for cluster members,
     * {@code memberAddress.getHost()} may return either an IP address or a hostname depending
     * on the Hazelcast configuration and network environment.
     *
     * <p>
     * <strong>Examples:</strong>
     * <ul>
     * <li>IPv4: {@code [192.168.1.1]:5701}</li>
     * <li>IPv6: {@code [2001:db8::1]:5701}</li>
     * <li>Hostname: {@code [artemis-node-1]:5701}</li>
     * <li>Client not connected (fallback): {@code artemis-build-agent-1/Artemis-client}</li>
     * </ul>
     *
     * <p>
     * <strong>Important:</strong> When using asyncStart=true for Hazelcast clients, the endpoint
     * address may not be available immediately. In this case, a unique fallback identifier
     * combining hostname and instance name (format: {@code hostname/instanceName}) is returned.
     * This ensures that each build agent has a unique key in the distributed map even before
     * connecting to the cluster.
     *
     * @return a unique identifier for this instance: the address in format {@code [host]:port},
     *         or {@code hostname/instanceName} if the client endpoint is not yet connected
     * @throws HazelcastInstanceNotActiveException if the Hazelcast instance is not running
     */
    @Override
    public String getLocalMemberAddress() {
        if (!isInstanceRunning()) {
            throw new HazelcastInstanceNotActiveException();
        }

        // Check if this is a Hazelcast client (build agents in client mode)
        if (hazelcastInstance instanceof HazelcastClientProxy) {
            // For clients with asyncStart=true, getLocalEndpoint() may return null
            // if the client hasn't connected to the cluster yet
            var localEndpoint = hazelcastInstance.getLocalEndpoint();
            if (localEndpoint == null || localEndpoint.getSocketAddress() == null) {
                // Use hostname + instance name as a unique fallback identifier.
                // This ensures each build agent has a unique key in the distributed map
                // even before connecting to the cluster.
                return getUniqueInstanceIdentifier();
            }
            // Verify the socket address is an InetSocketAddress before casting
            // In practice, Hazelcast always returns InetSocketAddress, but we guard against
            // potential future changes or unexpected implementations
            if (!(localEndpoint.getSocketAddress() instanceof InetSocketAddress socketAddress)) {
                return getUniqueInstanceIdentifier();
            }
            // Format as [host]:port for consistency
            return "[" + socketAddress.getAddress().getHostAddress() + "]:" + socketAddress.getPort();
        }

        // For cluster members, format consistently with client addresses
        var memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress();
        return "[" + memberAddress.getHost() + "]:" + memberAddress.getPort();
    }

    /**
     * Generates a unique identifier for this instance when the endpoint address is not available.
     * This is used as a fallback for Hazelcast clients with asyncStart=true before they connect.
     *
     * @return a unique identifier combining hostname and Hazelcast instance name
     */
    private String getUniqueInstanceIdentifier() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            hostname = "unknown-host";
        }
        // Combine hostname with instance name to ensure uniqueness across hosts
        // even if they share the same Hazelcast instance name configuration
        return hostname + "/" + hazelcastInstance.getName();
    }

    /**
     * Retrieves the addresses of all members in the Hazelcast cluster.
     *
     * @return a set of addresses of all cluster members, never null (returns empty set if no members or not connected)
     */
    @Override
    @Nonnull
    public Set<String> getClusterMemberAddresses() {
        // stream is on the copy so fine here
        return getClusterMembers().stream().map(Member::getAddress).map(Object::toString).collect(Collectors.toSet());
    }

    /**
     * Checks if there are no data members available in the cluster.
     *
     * @return {@code true} if all members in the cluster are lite members (i.e., no data members are available),
     */
    @Override
    public boolean noDataMemberInClusterAvailable() {
        // stream is on the copy so fine here
        return getClusterMembers().stream().allMatch(Member::isLiteMember);
    }

    /**
     * Retrieves the members of the Hazelcast cluster.
     *
     * @return a stream of Hazelcast cluster members
     */
    private ArrayList<Member> getClusterMembers() {
        if (!isInstanceRunning()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(hazelcastInstance.getCluster().getMembers());
    }

    /**
     * Gets the names of all connected Hazelcast clients.
     * This is only available on data members (core nodes), not on clients (build agents).
     * The client name corresponds to the Hazelcast instance name set when creating the client,
     * which is configured to be the build agent short name.
     *
     * @return a set of connected client names, or empty set if running as a client
     */
    @Override
    public Set<String> getConnectedClientNames() {
        if (!isInstanceRunning()) {
            return Set.of();
        }

        // Client service is only available on cluster members, not on clients
        if (hazelcastInstance instanceof HazelcastClientProxy) {
            return Set.of();
        }

        try {
            var clientService = hazelcastInstance.getClientService();
            return clientService.getConnectedClients().stream().map(client -> client.getName()).collect(Collectors.toSet());
        }
        catch (UnsupportedOperationException e) {
            // Client service not available
            return Set.of();
        }
    }

    /**
     * Checks if the Hazelcast instance is connected and ready to use.
     * For cluster members, this is equivalent to isInstanceRunning().
     * For clients (build agents with asyncStart=true), this checks if the client
     * has established a connection to at least one cluster member.
     *
     * @return true if connected and ready, false otherwise
     */
    @Override
    public boolean isConnectedToCluster() {
        if (!isInstanceRunning()) {
            return false;
        }

        // For cluster members, being running means being connected
        if (!(hazelcastInstance instanceof HazelcastClientProxy clientProxy)) {
            return true;
        }

        // For clients, check if connected to the cluster by verifying the client lifecycle state
        // A client in CONNECTED state has at least one active connection to a cluster member
        try {
            var lifecycleService = clientProxy.getLifecycleService();
            // isRunning() returns true even for async clients that haven't connected yet
            // We need to check if we can actually perform operations
            // The safest way is to check if the cluster is accessible
            return lifecycleService.isRunning() && clientProxy.getCluster().getMembers().size() > 0;
        }
        catch (Exception e) {
            // Any exception means we're not properly connected
            return false;
        }
    }

    /**
     * Registers a callback that will be invoked when the Hazelcast client connects or reconnects
     * to the cluster. The callback receives true for initial connection, false for reconnection.
     *
     * <p>
     * For Hazelcast clients (build agents), this uses the CLIENT_CONNECTED lifecycle event.
     * For cluster members, this uses the STARTED lifecycle event.
     *
     * <p>
     * If the client is already connected when this method is called, the callback will be
     * invoked immediately with isInitialConnection=true if this is the first listener, or
     * with the appropriate value based on connection history.
     *
     * @param callback a consumer that receives true for initial connection, false for reconnection
     * @return a unique identifier that can be used to remove the listener later
     */
    @Override
    public UUID addConnectionStateListener(Consumer<Boolean> callback) {
        UUID listenerId = UUID.randomUUID();
        connectionStateListeners.put(listenerId, callback);

        // If already connected, notify the listener immediately
        // This handles the case where a service registers a listener after initial connection
        if (isConnectedToCluster()) {
            try {
                // For late registrations after initial connection, treat as initial
                callback.accept(true);
            }
            catch (Exception e) {
                log.error("Error invoking connection state callback on registration: {}", e.getMessage(), e);
            }
        }

        return listenerId;
    }

    /**
     * Removes a previously registered connection state listener.
     *
     * @param listenerId the unique identifier returned by {@link #addConnectionStateListener}
     * @return true if the listener was found and removed, false otherwise
     */
    @Override
    public boolean removeConnectionStateListener(UUID listenerId) {
        return connectionStateListeners.remove(listenerId) != null;
    }
}
