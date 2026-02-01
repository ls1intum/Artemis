package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.annotation.PreDestroy;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedDataProviderService.class);

    /**
     * Polling interval for client disconnection detection in seconds.
     * Redis doesn't have built-in client disconnection events like Hazelcast,
     * so we poll the client list periodically to detect disconnections.
     */
    private static final long CLIENT_POLLING_INTERVAL_SECONDS = 5;

    @Value("${spring.data.redis.client-name:artemis-node}")
    private String redisClientName;

    private final RedissonClient redissonClient;

    private final RedisClientListResolver redisClientListResolver;

    /**
     * Registered client disconnection listeners. The callback receives the disconnected client's name.
     */
    private final Map<UUID, Consumer<String>> clientDisconnectionListeners = new ConcurrentHashMap<>();

    /**
     * Registered connection state listeners. The callback receives the connection state.
     */
    private final Map<UUID, Consumer<Boolean>> connectionStateListeners = new ConcurrentHashMap<>();

    /**
     * Tracks the previously known connected clients for detecting disconnections.
     */
    private volatile Set<String> previouslyKnownClients = new HashSet<>();

    /**
     * Scheduled executor for polling client connections.
     */
    private ScheduledExecutorService clientPollingExecutor;

    /**
     * The scheduled future for the client polling task.
     */
    private ScheduledFuture<?> clientPollingFuture;

    /**
     * Lock object for synchronizing access to the polling executor.
     */
    private final Object pollingLock = new Object();

    public RedissonDistributedDataProviderService(RedissonClient redissonClient, RedisClientListResolver redisClientListResolver) {
        this.redissonClient = redissonClient;
        this.redisClientListResolver = redisClientListResolver;
    }

    /**
     * Cleans up resources when the service is destroyed.
     */
    @PreDestroy
    public void destroy() {
        stopClientPolling();
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
        return new RedissonDistributedTopic<>(redissonClient.getTopic(name));
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
        UUID listenerId = UUID.randomUUID();
        connectionStateListeners.put(listenerId, callback);
        if (isConnectedToCluster()) {
            try {
                callback.accept(true);
            }
            catch (Exception e) {
                log.error("Error notifying connection state listener {}: {}", listenerId, e.getMessage(), e);
            }
        }
        return listenerId;
    }

    @Override
    public boolean removeConnectionStateListener(UUID listenerId) {
        return connectionStateListeners.remove(listenerId) != null;
    }

    @Override
    public UUID addClientDisconnectionListener(Consumer<String> callback) {
        // Start polling if this is the first listener
        startClientPollingIfNeeded();

        UUID listenerId = UUID.randomUUID();
        clientDisconnectionListeners.put(listenerId, callback);
        log.debug("Added client disconnection listener with ID: {}", listenerId);
        return listenerId;
    }

    @Override
    public boolean removeClientDisconnectionListener(UUID listenerId) {
        boolean removed = clientDisconnectionListeners.remove(listenerId) != null;
        if (removed) {
            log.debug("Removed client disconnection listener with ID: {}", listenerId);
            // Stop polling if no more listeners
            if (clientDisconnectionListeners.isEmpty()) {
                stopClientPolling();
            }
        }
        return removed;
    }

    /**
     * Starts the client polling task if it hasn't been started yet.
     * This is called lazily when the first listener is registered.
     */
    private void startClientPollingIfNeeded() {
        synchronized (pollingLock) {
            if (clientPollingExecutor == null || clientPollingExecutor.isShutdown()) {
                clientPollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "redis-client-polling");
                    thread.setDaemon(true);
                    return thread;
                });

                // Initialize with current clients to avoid false disconnection events on startup
                previouslyKnownClients = new HashSet<>(redisClientListResolver.getUniqueClients());
                log.info("Starting Redis client disconnection polling with interval of {} seconds. Initial clients: {}", CLIENT_POLLING_INTERVAL_SECONDS, previouslyKnownClients);

                clientPollingFuture = clientPollingExecutor.scheduleAtFixedRate(this::checkForDisconnectedClients, CLIENT_POLLING_INTERVAL_SECONDS, CLIENT_POLLING_INTERVAL_SECONDS,
                        TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Stops the client polling task and shuts down the executor.
     */
    private void stopClientPolling() {
        synchronized (pollingLock) {
            if (clientPollingFuture != null) {
                clientPollingFuture.cancel(false);
                clientPollingFuture = null;
            }
            if (clientPollingExecutor != null && !clientPollingExecutor.isShutdown()) {
                log.info("Stopping Redis client disconnection polling");
                clientPollingExecutor.shutdown();
                try {
                    if (!clientPollingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        clientPollingExecutor.shutdownNow();
                    }
                }
                catch (InterruptedException e) {
                    clientPollingExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                clientPollingExecutor = null;
            }
        }
    }

    /**
     * Checks for disconnected clients by comparing the current client list with the previously known clients.
     * Notifies all registered listeners for each disconnected client.
     */
    private void checkForDisconnectedClients() {
        if (!isInstanceRunning() || clientDisconnectionListeners.isEmpty()) {
            return;
        }

        try {
            Set<String> currentClients = redisClientListResolver.getUniqueClients();
            Set<String> disconnectedClients = new HashSet<>(previouslyKnownClients);
            disconnectedClients.removeAll(currentClients);

            for (String disconnectedClient : disconnectedClients) {
                log.info("Detected Redis client disconnection: {}", disconnectedClient);
                notifyClientDisconnectionListeners(disconnectedClient);
            }

            // Update the known clients for the next check
            previouslyKnownClients = new HashSet<>(currentClients);
        }
        catch (Exception e) {
            log.warn("Error checking for disconnected Redis clients: {}", e.getMessage());
        }
    }

    /**
     * Notifies all registered client disconnection listeners about a client disconnection.
     *
     * @param clientName the name of the disconnected client
     */
    private void notifyClientDisconnectionListeners(String clientName) {
        for (var entry : clientDisconnectionListeners.entrySet()) {
            try {
                entry.getValue().accept(clientName);
            }
            catch (Exception e) {
                log.error("Error notifying client disconnection listener {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
    }
}
