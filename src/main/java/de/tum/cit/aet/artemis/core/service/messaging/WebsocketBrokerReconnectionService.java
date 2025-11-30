package de.tum.cit.aet.artemis.core.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.tcp.TcpOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * Manages the lifecycle of the STOMP broker relay connection and coordinates reconnect attempts.
 * <p>
 * Tracks broker availability events, exposes manual connect/disconnect/reconnect hooks (used by admin actions),
 * and publishes the current broker status into Hazelcast for UI consumption. Admin-triggered disconnects suppress
 * automatic reconnect attempts until an explicit connect/reconnect request clears the suppression.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class WebsocketBrokerReconnectionService implements ApplicationListener<BrokerAvailabilityEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(WebsocketBrokerReconnectionService.class);

    public static final String WEBSOCKET_BROKER_STATUS_MAP = "websocketBrokerStatus";

    static final Duration RECONNECT_INTERVAL = Duration.ofSeconds(60);

    private static final Duration STATUS_PUBLISH_INTERVAL = Duration.ofSeconds(10);

    private final TaskScheduler messageBrokerTaskScheduler;

    private final Optional<StompBrokerRelayMessageHandler> stompBrokerRelayMessageHandler;

    private final HazelcastInstance hazelcastInstance;

    private IMap<String, Boolean> brokerStatusMap;

    private String localMemberId;

    private final Supplier<TcpOperations<byte[]>> stompTcpClientSupplier;

    private final AtomicBoolean reconnectTaskRunning = new AtomicBoolean(false);

    private final AtomicBoolean restartInProgress = new AtomicBoolean(false);

    /**
     * Flag to suppress automatic reconnect attempts after an admin explicitly disconnected the broker relay.
     */
    private final AtomicBoolean manualDisconnectRequested = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> reconnectTask;

    private volatile ScheduledFuture<?> statusPublishTask;

    private volatile boolean lastKnownBrokerAvailable = false;

    public WebsocketBrokerReconnectionService(@Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler,
            Optional<StompBrokerRelayMessageHandler> stompBrokerRelayMessageHandler,
            @Qualifier("websocketBrokerTcpClientSupplier") Supplier<TcpOperations<byte[]>> stompTcpClientSupplier,
            @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.stompBrokerRelayMessageHandler = stompBrokerRelayMessageHandler;
        this.stompTcpClientSupplier = stompTcpClientSupplier;
        this.hazelcastInstance = hazelcastInstance;
    }

    @PostConstruct
    void initBrokerStatusPublisher() {
        this.brokerStatusMap = hazelcastInstance.getMap(WEBSOCKET_BROKER_STATUS_MAP);
        this.localMemberId = hazelcastInstance.getCluster().getLocalMember().getUuid().toString();
        updateBrokerStatus(false);
        scheduleStatusPublisher();
    }

    @Override
    public void onApplicationEvent(BrokerAvailabilityEvent event) {
        if (stompBrokerRelayMessageHandler.isEmpty()) {
            return;
        }

        updateBrokerStatus(event.isBrokerAvailable());
        if (event.isBrokerAvailable()) {
            manualDisconnectRequested.set(false);
            stopReconnectAttempts("broker became available again");
        }
        else {
            if (manualDisconnectRequested.get()) {
                log.info("Broker became unavailable but manual disconnect is active; skipping auto-reconnect");
                return;
            }
            startReconnectAttempts("broker became unavailable");
        }
    }

    /**
     * Allows to manually trigger a reconnect to the external websocket broker.
     *
     * @return true if reconnect attempts were started, false if no external broker relay is configured
     */
    public boolean triggerManualReconnect() {
        if (stompBrokerRelayMessageHandler.isEmpty()) {
            log.warn("Manual websocket broker reconnect requested, but no external broker relay is configured");
            return false;
        }

        stopReconnectAttempts("manual reconnect requested");
        manualDisconnectRequested.set(false);
        startReconnectAttempts("manual reconnect requested");
        return true;
    }

    public boolean triggerManualDisconnect() {
        if (stompBrokerRelayMessageHandler.isEmpty()) {
            log.warn("Manual websocket broker disconnect requested, but no external broker relay is configured");
            return false;
        }

        stopReconnectAttempts("manual disconnect requested");
        manualDisconnectRequested.set(true);
        stompBrokerRelayMessageHandler.ifPresent(handler -> {
            if (handler.isRunning()) {
                handler.stop();
            }
        });
        updateBrokerStatus(false);
        return true;
    }

    /**
     * Manually start the broker relay without scheduling repeated reconnect attempts.
     *
     * @return true if the broker relay exists, false otherwise
     */
    public boolean triggerManualConnect() {
        if (stompBrokerRelayMessageHandler.isEmpty()) {
            log.warn("Manual websocket broker connect requested, but no external broker relay is configured");
            return false;
        }

        stopReconnectAttempts("manual connect requested");
        manualDisconnectRequested.set(false);
        return restartBrokerRelayInternal(true);
    }

    private void startReconnectAttempts(String reason) {
        if (reconnectTaskRunning.compareAndSet(false, true)) {
            log.warn("Starting websocket broker reconnect attempts because {}", reason);
            updateBrokerStatus(false);
            restartBrokerRelay();
            reconnectTask = messageBrokerTaskScheduler.scheduleWithFixedDelay(this::restartBrokerRelay, Instant.now(), RECONNECT_INTERVAL);
        }
    }

    private void restartBrokerRelay() {
        if (!reconnectTaskRunning.get()) {
            return;
        }

        restartBrokerRelayInternal(false);
    }

    private boolean restartBrokerRelayInternal(boolean explicitRequest) {
        stompBrokerRelayMessageHandler.ifPresent(handler -> {
            if (!restartInProgress.compareAndSet(false, true)) {
                return; // Avoid overlapping restart attempts
            }

            try {
                if (handler.isRunning()) {
                    handler.stop();
                }
                TcpOperations<byte[]> tcpClient = stompTcpClientSupplier.get();
                if (tcpClient == null) {
                    log.warn("Skipping websocket broker restart because no TCP client could be created");
                    return;
                }
                handler.setTcpClient(tcpClient);
                handler.start();
            }
            catch (Exception ex) {
                log.warn("Failed to restart websocket broker relay: {}", ex.getMessage(), ex);
            }
            finally {
                restartInProgress.set(false);
            }
        });
        if (explicitRequest) {
            updateBrokerStatus(false);
        }
        return stompBrokerRelayMessageHandler.isPresent();
    }

    /**
     * Cancel running reconnect attempts and clear scheduled tasks.
     *
     * @param reason diagnostic reason for logging
     */
    private void stopReconnectAttempts(String reason) {
        if (reconnectTaskRunning.getAndSet(false)) {
            log.info("Stopping websocket broker reconnect attempts because {}", reason);
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
                reconnectTask = null;
            }
        }
    }

    @Override
    public void destroy() {
        stopReconnectAttempts("application shutdown");
        if (brokerStatusMap != null) {
            brokerStatusMap.remove(localMemberId);
        }
        if (statusPublishTask != null) {
            statusPublishTask.cancel(false);
        }
    }

    /**
     * Store the current broker availability in a distributed map that is read by the admin UI.
     *
     * @param brokerAvailable whether the broker is currently available for this node
     */
    private void updateBrokerStatus(boolean brokerAvailable) {
        lastKnownBrokerAvailable = brokerAvailable;
        if (brokerStatusMap != null) {
            brokerStatusMap.put(localMemberId, brokerAvailable);
        }
    }

    /**
     * Periodically re-publish the last known broker status so nodes that join late (e.g. when Hazelcast is not yet ready)
     * eventually see an up-to-date value.
     */
    private void scheduleStatusPublisher() {
        statusPublishTask = messageBrokerTaskScheduler.scheduleWithFixedDelay(() -> {
            try {
                brokerStatusMap.put(localMemberId, lastKnownBrokerAvailable);
                log.info("Published websocket broker status {} for member {}", lastKnownBrokerAvailable, localMemberId);
            }
            catch (Exception ex) {
                log.warn("Failed to publish websocket broker status: {}", ex.getMessage());
            }
        }, Instant.now().plusSeconds(5), STATUS_PUBLISH_INTERVAL);
    }

    public enum ControlAction {
        RECONNECT, DISCONNECT, CONNECT
    }
}
