package de.tum.cit.aet.artemis.programming.service.localci.distributedData.hazelcast;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;

import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.DistributedTopic;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.queue.DistributedQueue;

@Service
public class HazelcastDistributedDataProviderService implements DistributedDataProvider {

    private final HazelcastInstance hazelcastInstance;

    public HazelcastDistributedDataProviderService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name) {
        return new HazelcastDistributedQueue<>(hazelcastInstance.getQueue(name));
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
        return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
    }

    /**
     * @return the address of the local Hazelcast member
     */
    @Override
    public String getLocalMemberAddress() {
        if (!isInstanceRunning()) {
            throw new HazelcastInstanceNotActiveException();
        }
        return hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
    }

    /**
     * Retrieves the addresses of all members in the Hazelcast cluster.
     *
     * @return a set of addresses of all cluster members
     */
    @Override
    public Set<String> getClusterMemberAddresses() {
        return getClusterMembers().map(Member::getAddress).map(Object::toString).collect(Collectors.toSet());
    }

    /**
     * Checks if there are no data members available in the cluster.
     *
     * @return {@code true} if all members in the cluster are lite members (i.e., no data members are available),
     */
    @Override
    public boolean noDataMemberInClusterAvailable() {
        return getClusterMembers().allMatch(Member::isLiteMember);
    }

    /**
     * Retrieves the members of the Hazelcast cluster.
     *
     * @return a stream of Hazelcast cluster members
     */
    private Stream<Member> getClusterMembers() {
        if (!isInstanceRunning()) {
            return Stream.empty();
        }
        return hazelcastInstance.getCluster().getMembers().stream();
    }
}
