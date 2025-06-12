package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api;

import java.util.Set;

import com.hazelcast.config.Config;

import de.tum.cit.aet.artemis.core.config.CacheConfiguration;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.topic.DistributedTopic;
import tech.jhipster.config.JHipsterProperties;

public interface DistributedDataProvider {

    <T> DistributedQueue<T> getQueue(String name);

    /**
     * Returns a priority queue with the given name.<br>
     * For internal Hazelcast IQueue usage the comparator needs to be set in the config at startup, see {@link CacheConfiguration#configureQueueCluster(Config, JHipsterProperties)}
     * for an example*
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
