package de.tum.cit.aet.artemis.programming.service.localci.distributed.api;

import java.util.Set;

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
     * For internal Hazelcast IQueue usage the comparator needs to be set in the config at startup, see {@link de.tum.cit.aet.artemis.core.config.CacheConfiguration}
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
     * @return a set of addresses of all cluster members
     */
    Set<String> getClusterMemberAddresses();

    /**
     * Checks if there are no data members available in the cluster.
     *
     * @return true if no data members are available, false otherwise
     */
    boolean noDataMemberInClusterAvailable();
}
