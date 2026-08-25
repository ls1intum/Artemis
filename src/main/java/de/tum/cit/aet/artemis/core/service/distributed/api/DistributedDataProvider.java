package de.tum.cit.aet.artemis.core.service.distributed.api;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;

import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.api.set.DistributedSet;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;

/**
 * The DistributedDataProvider acts as an abstraction layer for accessing distributed data structures
 * like queues, maps, and topics across nodes. It enables nodes (e.g. build agents with core node) to communicate and share data.
 * <p>
 * <ul>
 * <li>Provides a unified API for accessing distributed data structures regardless of the underlying implementation</li>
 * <li>Abstracts implementation details (like e.g. Hazelcast) for extensibility and flexibility</li>
 * </ul>
 * <p>
 * This interface is currently used for the Local CI system to manage build job queues, process results,
 * and track build agent information.
 */
public interface DistributedDataProvider {

    /**
     * Returns a distributed queue with the given name.
     *
     * @param name the name of the queue
     * @param <T>  the type of elements in the queue
     * @return a DistributedQueue with the specified name
     */
    <T> DistributedQueue<T> getQueue(String name);

    /**
     * Returns a priority queue with the given name.
     *
     * <p>
     * For internal Hazelcast IQueue usage the comparator needs to be set in the config at startup, see {@link de.tum.cit.aet.artemis.core.config.HazelcastConfiguration}
     * for an example.
     *
     * @param name the name of the priority queue
     * @param <T>  the type of elements in the queue
     * @return a DistributedQueue that behaves like a priority queue
     *
     */
    <T extends Comparable<T>> DistributedQueue<T> getPriorityQueue(String name);

    /**
     * Returns a distributed map with the given name.
     *
     * @param name the name of the map
     * @param <K>  the type of keys in the map
     * @param <V>  the type of values in the map
     * @return a DistributedMap with the specified name
     */
    <K, V> DistributedMap<K, V> getMap(String name);

    /**
     * Returns a map whose entries expire.
     *
     * <p>
     * Entries stored through {@link DistributedMap#put(Object, Object)} expire after {@code defaultTimeToLive}; callers
     * that need a different lifetime for individual entries use
     * {@link DistributedMap#put(Object, Object, java.time.Duration)}. Maps from {@link #getMap(String)} never expire and
     * reject the per-entry TTL overload, so the expiry requirement is visible at the call site rather than buried in
     * backend configuration.
     *
     * @param <K>               key type
     * @param <V>               value type
     * @param name              the map name
     * @param defaultTimeToLive how long entries stored without an explicit TTL remain readable
     * @return an expiring distributed map
     */
    <K, V> DistributedMap<K, V> getExpiringMap(String name, Duration defaultTimeToLive);

    /**
     * Returns a distributed topic with the given name.
     *
     * @param name the name of the topic
     * @param <T>  the type of messages in the topic
     * @return a DistributedTopic with the specified name
     */
    <T> DistributedTopic<T> getTopic(String name);

    /**
     * Returns a topic that does not drop messages when a subscriber is briefly disconnected or slow.
     *
     * <p>
     * {@link #getTopic(String)} is fire-and-forget on every backend, which is fine for state that self-heals on the next
     * heartbeat (a pause command, a broker reconnect hint). Use a reliable topic where losing a single message has a
     * lasting effect, such as the scheduling messages: a dropped one means an exercise or quiz is never scheduled.
     *
     * @param name the topic name
     * @param <T>  message type
     * @return a reliable distributed topic
     */
    <T> DistributedTopic<T> getReliableTopic(String name);

    /**
     * Returns a distributed set with the given name.
     *
     * @param name the set name
     * @param <T>  element type
     * @return a distributed set
     */
    <T> DistributedSet<T> getSet(String name);

    /**
     * Returns a cluster-wide lock with the given name.
     *
     * <p>
     * See {@link DistributedLock} for the (deliberately weak) guarantees this provides.
     *
     * @param name the lock name
     * @return a distributed lock
     */
    DistributedLock getLock(String name);

    /**
     * Checks if the distributed data provider instance is running.
     *
     * @return true if the instance is running, false otherwise
     */
    boolean isInstanceRunning();

    /**
     * Gets the address of the local member in the cluster.
     *
     * @return the address of the local member
     */
    String getLocalMemberAddress();

    /**
     * Gets the addresses of all cluster members.
     *
     * @return a set of addresses of all cluster members, never null (returns empty set if no members or not connected)
     */
    @NonNull
    Set<String> getClusterMemberAddresses();

    /**
     * Whether build agents are expected to appear in {@link #getClusterMemberAddresses()}.
     *
     * <p>
     * This decides how absence from that set may be interpreted. Hazelcast build agents connect as clients and are
     * never cluster members, so their absence carries no information and they have to be detected as gone through
     * {@link #addClientDisconnectionListener(Consumer)} instead. Providers without a member/client distinction report
     * every node, so for them absence does mean the node is gone.
     *
     * @return true if an absent build agent can be treated as offline
     */
    boolean buildAgentsAppearInClusterMemberList();

    /**
     * Checks if there are no data members available in the cluster.
     *
     * @return true if no data members are available, false otherwise
     */
    boolean noDataMemberInClusterAvailable();

    /**
     * Gets the names of all connected clients in the cluster.
     * This is only available on data members (core nodes), not on clients (build agents).
     * On clients, this returns an empty set.
     *
     * @return a set of connected client names, or empty set if running as a client or not supported
     */
    Set<String> getConnectedClientNames();

    /**
     * Gets the remote addresses each connected client is observed to connect from, keyed by client name.
     * <p>
     * The addresses are the ones the middleware sees on the client's own connection, not values the client
     * reported about itself. That distinction is the point of this method: a build agent's self-reported
     * {@code memberAddress} is its local pre-NAT socket and is forgeable, whereas the observed address is
     * whatever the connection actually came from and is therefore usable for authorizing git requests.
     * <p>
     * A client behind NAT appears under the address of its gateway, so several agents can legitimately share
     * one address, and one agent can appear under several addresses while it reconnects.
     * <p>
     * Like {@link #getConnectedClientNames()}, this is only meaningful on data members (core nodes). Providers
     * that cannot observe client connections return an empty map, which callers must treat as "unknown", never
     * as "no client is connected".
     *
     * @return connected client name to its observed remote addresses (host only, without port), or
     *         {@link Optional#empty()} when this question cannot be answered at all - running as a client, an
     *         unsupported provider, or a failed query. An empty {@code Optional} and an empty map mean different things
     *         and callers must not conflate them: the first is "unknown", the second is "nothing is connected", and
     *         treating a failed query as the latter would drop every registered address.
     */
    Optional<Map<String, Set<String>>> getConnectedClientAddresses();

    /**
     * Whether a client's connection to this middleware terminates on an Artemis core node.
     * <p>
     * Decides whether the addresses from {@link #getConnectedClientAddresses()} may be used to authorize a git
     * request, and the answer is a property of the topology rather than of the query:
     * <ul>
     * <li><b>Hazelcast</b> clients connect to the cluster members, which are the core nodes that also serve git. A
     * build agent therefore reaches both over the same path, so the address the middleware observed is the address its
     * clone will arrive from.</li>
     * <li><b>Redis</b> is a separate service. An agent's connection to it says nothing about the route it takes to a
     * core node, and the two genuinely differ: with Redis in a container and the nodes on the host, the middleware
     * observes the docker bridge gateway while git sees loopback. Comparing them refuses every clone.</li>
     * </ul>
     * A provider answering {@code false} still reports connected clients and their addresses - both remain useful for
     * liveness and for the admin overview - but nothing observed here may authorize a git request. The origin binding
     * is not lost on such a provider: {@code BuildAgentAddressReportingService} has each agent ask a core node over the
     * git path which address it arrives from, so the binding is established by measurement on the right path instead.
     * This flag decides only which of the two routes supplies it.
     *
     * @return whether an observed client address is also the address that client reaches the git server from
     */
    boolean clientsConnectDirectlyToCoreNodes();

    /**
     * Checks if the distributed data provider is connected and ready to use.
     * For cluster members, this is equivalent to isInstanceRunning().
     * For clients (e.g., build agents), this checks if the client has established
     * a connection to at least one cluster member.
     *
     * <p>
     * This is important for async-start clients that may be running but not yet
     * connected to the cluster. Operations on distributed objects will fail until
     * the client is connected.
     *
     * @return true if the instance is connected and ready to use, false otherwise
     */
    boolean isConnectedToCluster();

    /**
     * Registers a callback that will be invoked when the client reconnects to the cluster
     * after a disconnection. This is important for re-registering listeners on distributed
     * objects, as they may be lost when the connection is interrupted.
     *
     * <p>
     * For cluster members (core nodes), this callback may never be invoked since members
     * don't "reconnect" in the same way clients do. The callback is primarily useful for
     * Hazelcast clients (build agents) that may disconnect and reconnect.
     *
     * <p>
     * The callback receives a boolean indicating whether this is the initial connection
     * (true) or a reconnection after disconnection (false). This allows services to
     * differentiate between first-time setup and re-initialization after connection loss.
     *
     * @param callback a consumer that receives true for initial connection, false for reconnection
     * @return a unique identifier that can be used to remove the listener later
     */
    UUID addConnectionStateListener(Consumer<Boolean> callback);

    /**
     * Removes a previously registered connection state listener.
     *
     * @param listenerId the unique identifier returned by {@link #addConnectionStateListener}
     * @return true if the listener was found and removed, false otherwise
     */
    boolean removeConnectionStateListener(UUID listenerId);

    /**
     * Registers a callback that will be invoked when a client (build agent) disconnects from the cluster.
     * This is only available on data members (core nodes), not on clients (build agents).
     * On clients, this method returns null and the callback is never invoked.
     *
     * <p>
     * This is important for cleaning up stale data when build agents crash or disconnect
     * unexpectedly. The callback receives the client name (build agent short name) that disconnected.
     *
     * @param callback a consumer that receives the disconnected client's name
     * @return a unique identifier that can be used to remove the listener later, or null if not supported
     */
    UUID addClientDisconnectionListener(Consumer<String> callback);

    /**
     * Removes a previously registered client disconnection listener.
     *
     * @param listenerId the unique identifier returned by {@link #addClientDisconnectionListener}
     * @return true if the listener was found and removed, false otherwise
     */
    boolean removeClientDisconnectionListener(UUID listenerId);
}
