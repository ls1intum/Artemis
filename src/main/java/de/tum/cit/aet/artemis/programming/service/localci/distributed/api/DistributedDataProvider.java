package de.tum.cit.aet.artemis.programming.service.localci.distributed.api;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

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
     * Returns a distributed topic with the given name.
     *
     * @param name the name of the topic
     * @param <T>  the type of messages in the topic
     * @return a DistributedTopic with the specified name
     */
    <T> DistributedTopic<T> getTopic(String name);

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
