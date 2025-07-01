package de.tum.cit.aet.artemis.programming.service.localci.distributed.hazelcast;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.topic.DistributedTopic;

@Lazy
@Service
@Profile({ PROFILE_LOCALCI, PROFILE_BUILDAGENT })
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
}
