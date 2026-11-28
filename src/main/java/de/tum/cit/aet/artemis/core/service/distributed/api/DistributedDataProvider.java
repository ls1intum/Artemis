package de.tum.cit.aet.artemis.core.service.distributed.api;

import java.time.Duration;
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
     * provider configuration.
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
     * {@link #getTopic(String)} is fire-and-forget on every provider, which is fine for state that self-heals on the next
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
     * @return whether the provider is running and can service requests
     */
    boolean isConnectedToCluster();

    /**
     * Registers a callback invoked when an Artemis client disappears from Redis membership. Local has no remote clients.
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
