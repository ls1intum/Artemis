package de.tum.cit.aet.artemis.core.service.distributed.local;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.LocalDataCondition;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DefaultTimeToLiveDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.NonExpiringDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.api.set.DistributedSet;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;

@Service
@Lazy
@Conditional(LocalDataCondition.class)
public class LocalDataProviderService implements DistributedDataProvider {

    private final ConcurrentHashMap<String, DistributedQueue<?>> queues = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DistributedMap<?, ?>> maps = new ConcurrentHashMap<>();

    /**
     * Expiring maps are kept in their own registry. Sharing one backing map between {@link #getMap(String)} and
     * {@link #getExpiringMap(String, Duration)} would let a TTL write expire an entry out from under readers of the
     * non-expiring view, which that view promises never happens.
     */
    private final ConcurrentHashMap<String, DistributedMap<?, ?>> expiringMaps = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DistributedSet<?>> sets = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DistributedLock> locks = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DistributedTopic<?>> topics = new ConcurrentHashMap<>();

    @Override
    public <T> DistributedQueue<T> getQueue(String name) {
        // noinspection unchecked
        return (DistributedQueue<T>) queues.computeIfAbsent(name, n -> new LocalQueue<>(new ConcurrentLinkedQueue<T>(), n));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Requesting the same name once as a plain and once as a priority queue is rejected rather than silently answered
     * with whichever kind was created first. That is the same failure the Hazelcast backend guards against: a caller
     * that believes it holds a priority queue but is handed a FIFO one dispatches in the wrong order without any signal.
     */
    @Override
    public <T extends Comparable<T>> DistributedQueue<T> getPriorityQueue(String name) {
        // noinspection unchecked
        DistributedQueue<T> queue = (DistributedQueue<T>) queues.computeIfAbsent(name, n -> new LocalQueue<>(new PriorityQueue<T>(), n, Comparator.<T>naturalOrder()));
        if (!(queue instanceof LocalQueue<T> localQueue) || !localQueue.isOrdered()) {
            throw new UnsupportedOperationException(
                    "Queue '" + name + "' was already created as a plain FIFO queue, so it cannot be handed out as a priority queue. Use one kind per queue name.");
        }
        return queue;
    }

    @Override
    public <K, V> DistributedMap<K, V> getMap(String name) {
        // noinspection unchecked
        return new NonExpiringDistributedMap<>((DistributedMap<K, V>) maps.computeIfAbsent(name, _ -> new LocalMap<K, V>()), name);
    }

    @Override
    public <K, V> DistributedMap<K, V> getExpiringMap(String name, Duration defaultTimeToLive) {
        // LocalMap enforces per-entry expiry itself. Deliberately a separate registry from getMap(name): see expiringMaps.
        // noinspection unchecked
        return new DefaultTimeToLiveDistributedMap<>((DistributedMap<K, V>) expiringMaps.computeIfAbsent(name, _ -> new LocalMap<K, V>()), defaultTimeToLive);
    }

    @Override
    public <T> DistributedTopic<T> getTopic(String name) {
        // noinspection unchecked
        return (DistributedTopic<T>) topics.computeIfAbsent(name, _ -> new LocalTopic<T>());
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * On a single node a publish reaches its listeners synchronously and in-process, so there is nothing for delivery to
     * be unreliable about and the plain topic already satisfies the reliable contract.
     */
    @Override
    public <T> DistributedTopic<T> getReliableTopic(String name) {
        return getTopic(name);
    }

    @Override
    public <T> DistributedSet<T> getSet(String name) {
        // noinspection unchecked
        return (DistributedSet<T>) sets.computeIfAbsent(name, _ -> new LocalSet<T>());
    }

    @Override
    public DistributedLock getLock(String name) {
        return locks.computeIfAbsent(name, _ -> new LocalLock());
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

    /**
     * {@inheritDoc}
     *
     * <p>
     * The local provider models a single node that is both core and build agent, and it reports that node as alive, so
     * an agent missing from the set is genuinely unknown.
     */
    @Override
    public boolean buildAgentsAppearInClusterMemberList() {
        return true;
    }

    @Override
    public boolean noDataMemberInClusterAvailable() {
        return false;
    }

    @Override
    public Set<String> getConnectedClientNames() {
        // Local provider doesn't support client tracking - assume all registered agents are connected
        return Set.of();
    }

    @Override
    public Optional<Map<String, Set<String>>> getConnectedClientAddresses() {
        // Local provider has no client connections to observe: everything runs in one JVM. Empty rather than an empty
        // map, so callers treat this as "unknown" and skip the address binding, which is correct here - there is no
        // remote build agent whose origin could be checked.
        return Optional.empty();
    }

    @Override
    public boolean clientsConnectDirectlyToCoreNodes() {
        // Everything runs in one JVM, so there is no client connection at all to draw a conclusion from
        return false;
    }

    @Override
    public boolean isConnectedToCluster() {
        // Local provider is always "connected" (it's a single-node in-memory implementation)
        return isInstanceRunning();
    }

    @Override
    public UUID addConnectionStateListener(Consumer<Boolean> callback) {
        // Local provider is always connected - invoke callback immediately with isInitialConnection=true
        callback.accept(true);
        // Return a random UUID - listeners are not tracked since local provider doesn't have lifecycle events
        return UUID.randomUUID();
    }

    @Override
    public boolean removeConnectionStateListener(UUID listenerId) {
        // No-op for local provider - listeners are not tracked
        return false;
    }

    @Override
    public UUID addClientDisconnectionListener(Consumer<String> callback) {
        // Local provider doesn't support client disconnection tracking - return null
        return null;
    }

    @Override
    public boolean removeClientDisconnectionListener(UUID listenerId) {
        // No-op for local provider - listeners are not tracked
        return false;
    }
}
