package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.redisson.api.RedissonClient;
import org.redisson.config.ConfigSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.RedisDistributedDataCondition;
import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DefaultTimeToLiveDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.NonExpiringDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.api.set.DistributedSet;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;

// Exactly one provider bean exists at runtime, because the conditions of both match one value of
// artemis.distributed-data.provider each. Static analysis cannot evaluate those conditions and reports every injection of
// DistributedDataProvider as ambiguous (Sonar java:S9352); @Primary names the preferred provider for it and changes nothing at runtime.
@Lazy
@Service
@Primary
@Conditional(RedisDistributedDataCondition.class)
public class RedissonDistributedDataProviderService implements DistributedDataProvider {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedDataProviderService.class);

    /**
     * Polling interval for client disconnection detection in seconds.
     * Redis doesn't expose client disconnection events,
     * so we poll the client list periodically to detect disconnections.
     */
    private static final long CLIENT_POLLING_INTERVAL_SECONDS = 5;

    @Value("${artemis.version:unknown}")
    private String artemisVersion;

    private final RedissonClient redissonClient;

    private final RedisClientListResolver redisClientListResolver;

    /**
     * Registered client disconnection listeners. The callback receives the disconnected client's name.
     */
    private final Map<UUID, Consumer<String>> clientDisconnectionListeners = new ConcurrentHashMap<>();

    /**
     * Tracks the previously known connected clients for detecting disconnections.
     */
    private final AtomicReference<Set<String>> previouslyKnownClients = new AtomicReference<>(new HashSet<>());

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
     * Brings the store up to the schema version this build reads. Placed here rather than on a startup event because
     * ordering is what matters: nothing can obtain a structure before this bean exists, so nothing can read a
     * namespace the migration has not finished preparing.
     */
    @PostConstruct
    public void migrateDistributedData() {
        new RedissonDistributedDataMigrator(redissonClient, artemisVersion).migrateToCurrentVersion();
    }

    /**
     * Cleans up resources when the service is destroyed.
     */
    @PreDestroy
    public void destroy() {
        stopClientPolling();
    }

    /**
     * Prefixes a logical name with the namespace of the schema version this build reads, so that a release never sees
     * a key another version wrote. The logical name is what callers pass and what appears in log and error messages;
     * only what reaches Redis is prefixed.
     *
     * @param name the logical structure name
     * @return the Redis key it lives under
     */
    private static String key(String name) {
        return DistributedDataSchema.currentKeyFor(name);
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name) {
        return new RedissonDistributedQueue<>(redissonClient.getQueue(key(name)), redissonClient.getTopic(key(name) + ":queue_notification"), name);
    }

    @Override
    public <T extends Comparable<T>> DistributedQueue<T> getPriorityQueue(String name) {
        return new RedissonDistributedQueue<>(redissonClient.getPriorityQueue(key(name)), redissonClient.getTopic(key(name) + ":queue_notification"), name);
    }

    @Override
    public <K, V> DistributedMap<K, V> getMap(String name) {
        return new NonExpiringDistributedMap<>(new RedissonDistributedMap<>(redissonClient.getMap(key(name)), redissonClient.getTopic(key(name) + ":map_notification")), name);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Uses {@code RMapCache} rather than {@code RMap}, since only the former tracks per-entry expiry. Redisson enforces
     * the deadline on read and evicts in the background, so no keyspace-notification configuration is required.
     */
    @Override
    public <K, V> DistributedMap<K, V> getExpiringMap(String name, Duration defaultTimeToLive) {
        RedissonDistributedMap<K, V> expiringMap = new RedissonDistributedMap<>(redissonClient.<K, V>getMapCache(key(name)),
                redissonClient.getTopic(key(name) + ":map_notification"));
        return new DefaultTimeToLiveDistributedMap<>(expiringMap, defaultTimeToLive);
    }

    @Override
    public <T> DistributedTopic<T> getTopic(String name) {
        return new RedissonDistributedTopic<>(redissonClient.getTopic(key(name)));
    }

    @Override
    public <T> DistributedTopic<T> getReliableTopic(String name) {
        return new RedissonReliableDistributedTopic<>(redissonClient.getReliableTopic(key(name)));
    }

    @Override
    public <T> DistributedSet<T> getSet(String name) {
        return new RedissonDistributedSet<>(redissonClient.getSet(key(name)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Deliberately not namespaced. A lock carries no encoded payload, so a version prefix buys nothing, and it costs
     * the only thing a lock is for: during a rolling upgrade a node of the old and a node of the new schema version
     * would take different mutexes and both enter a section {@link DistributedLock} promises is cluster-wide, which is
     * how a scheduled digest or alert gets sent twice.
     */
    @Override
    public DistributedLock getLock(String name) {
        return new RedissonDistributedLock(redissonClient.getLock(name));
    }

    @Override
    public boolean isInstanceRunning() {
        return !redissonClient.isShutdown() && !redissonClient.isShuttingDown();
    }

    @Override
    public String getLocalMemberAddress() {
        return ConfigSupport.getConfig(redissonClient.getConfig()).getClientName();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Callers use the absence of a node from this set as proof that it is gone, so a partial answer must never be handed out. In Redis Cluster mode a single
     * {@code CLIENT LIST} only covers one node, so the resolver aggregates across the cluster and reports whether it saw everything. An incomplete lookup yields an
     * empty set, which every caller already treats as "no information" rather than "nothing is alive".
     */
    @Override
    public Set<String> getClusterMemberAddresses() {
        var snapshot = redisClientListResolver.resolveClients();
        return snapshot.complete() ? snapshot.clientNames() : Set.of();
    }

    @Override
    public boolean isConnectedToCluster() {
        // For Redis, being running means being connected
        return isInstanceRunning();
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
                previouslyKnownClients.set(new HashSet<>(redisClientListResolver.getUniqueClients()));
                log.info("Starting Redis client disconnection polling with interval of {} seconds. Initial clients: {}", CLIENT_POLLING_INTERVAL_SECONDS,
                        previouslyKnownClients.get());

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
            var snapshot = redisClientListResolver.resolveClients();
            if (!snapshot.complete()) {
                // A partial client list would make every client that this lookup did not see look disconnected and fire the listeners for it. Skip the round instead;
                // the next one runs a few seconds later.
                log.debug("Skipping the Redis client disconnection check because the client list was incomplete");
                return;
            }
            Set<String> currentClients = snapshot.clientNames();
            Set<String> disconnectedClients = new HashSet<>(previouslyKnownClients.get());
            disconnectedClients.removeAll(currentClients);

            for (String disconnectedClient : disconnectedClients) {
                log.info("Detected Redis client disconnection: {}", disconnectedClient);
                notifyClientDisconnectionListeners(disconnectedClient);
            }

            // Update the known clients for the next check
            previouslyKnownClients.set(new HashSet<>(currentClients));
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
