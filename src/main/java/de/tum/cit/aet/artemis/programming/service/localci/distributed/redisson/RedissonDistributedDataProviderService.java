package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.LocalCIBuildAgentRedisDataCondition;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

@Lazy
@Service
@Conditional(LocalCIBuildAgentRedisDataCondition.class)
public class RedissonDistributedDataProviderService implements DistributedDataProvider {

    @Value("${spring.data.redis.client-name:artemis-node}")
    private String redisClientName;

    private final RedissonClient redissonClient;

    private final RedisClientListResolver redisClientListResolver;

    public RedissonDistributedDataProviderService(RedissonClient redissonClient, RedisClientListResolver redisClientListResolver) {
        this.redissonClient = redissonClient;
        this.redisClientListResolver = redisClientListResolver;
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name) {
        return new RedissonDistributedQueue<>(redissonClient.getQueue(name), redissonClient.getTopic(name + ":queue_notification"));
    }

    @Override
    public <T extends Comparable<T>> DistributedQueue<T> getPriorityQueue(String name) {
        return new RedissonDistributedQueue<>(redissonClient.getPriorityQueue(name), redissonClient.getTopic(name + ":queue_notification"));
    }

    @Override
    public <K, V> DistributedMap<K, V> getMap(String name) {
        return new RedissonDistributedMap<>(redissonClient.getMap(name), redissonClient.getTopic(name + ":map_notification"));
    }

    @Override
    public <T> DistributedTopic<T> getTopic(String name) {
        return new RedissonDistributedTopic<T>(redissonClient.getTopic(name));
    }

    @Override
    public boolean isInstanceRunning() {
        return !redissonClient.isShutdown() && !redissonClient.isShuttingDown();
    }

    @Override
    public String getLocalMemberAddress() {
        return redisClientName;
    }

    @Override
    public Set<String> getClusterMemberAddresses() {
        return redisClientListResolver.getUniqueClients();
    }

    @Override
    public boolean noDataMemberInClusterAvailable() {
        return !isInstanceRunning();
    }

    @Override
    public Set<String> getConnectedClientNames() {
        // Redis doesn't have the concept of connected clients in the same way as Hazelcast
        // Return all known clients from the Redis client list
        return redisClientListResolver.getUniqueClients();
    }

    @Override
    public boolean isConnectedToCluster() {
        // For Redis, being running means being connected
        return isInstanceRunning();
    }

    @Override
    public UUID addConnectionStateListener(Consumer<Boolean> callback) {
        // Redis doesn't have the same connection lifecycle semantics as Hazelcast clients.
        // The connection is either available or not, and Redisson handles reconnection internally.
        // We immediately invoke the callback with isInitialConnection=true if connected.
        if (isConnectedToCluster()) {
            callback.accept(true);
        }
        // Return a random UUID - listeners are not tracked since Redis doesn't have lifecycle events
        return UUID.randomUUID();
    }

    @Override
    public boolean removeConnectionStateListener(UUID listenerId) {
        // No-op for Redis - listeners are not tracked
        return false;
    }
}
