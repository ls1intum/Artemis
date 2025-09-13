package de.tum.cit.aet.artemis.programming.service.localci.distributed.local;

import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.LocalDataCondition;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

@Service
@Lazy
@Conditional(LocalDataCondition.class)
public class LocalDataProviderService implements DistributedDataProvider {

    private final ConcurrentHashMap<String, DistributedQueue<?>> queues = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DistributedMap<?, ?>> maps = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DistributedTopic<?>> topics = new ConcurrentHashMap<>();

    @Override
    public <T> DistributedQueue<T> getQueue(String name) {
        // noinspection unchecked
        return (DistributedQueue<T>) queues.computeIfAbsent(name, n -> new LocalQueue<T>(new ConcurrentLinkedQueue<T>(), n));
    }

    @Override
    public <T extends Comparable<T>> DistributedQueue<T> getPriorityQueue(String name) {
        // noinspection unchecked
        return (DistributedQueue<T>) queues.computeIfAbsent(name, n -> new LocalQueue<T>(new PriorityQueue<T>(), n));
    }

    @Override
    public <K, V> DistributedMap<K, V> getMap(String name) {
        // noinspection unchecked
        return (DistributedMap<K, V>) maps.computeIfAbsent(name, n -> new LocalMap<K, V>());
    }

    @Override
    public <T> DistributedTopic<T> getTopic(String name) {
        // noinspection unchecked
        return (DistributedTopic<T>) topics.computeIfAbsent(name, n -> new LocalTopic<T>());
    }

    @Override
    public boolean isInstanceRunning() {
        return true;
    }

    @Override
    public String getLocalMemberAddress() {
        return "localhost";
    }

    @Override
    public Set<String> getClusterMemberAddresses() {
        return Set.of(getLocalMemberAddress());
    }

    @Override
    public boolean noDataMemberInClusterAvailable() {
        return false;
    }
}
