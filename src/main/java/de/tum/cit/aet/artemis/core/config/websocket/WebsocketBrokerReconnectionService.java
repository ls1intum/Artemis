package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Profile(PROFILE_CORE)
@Component
public class WebsocketBrokerReconnectionService implements ApplicationListener<BrokerAvailabilityEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(WebsocketBrokerReconnectionService.class);

    static final Duration RECONNECT_INTERVAL = Duration.ofSeconds(10);

    private final TaskScheduler messageBrokerTaskScheduler;

    private final Optional<StompBrokerRelayMessageHandler> stompBrokerRelayMessageHandler;

    private final AtomicBoolean reconnectTaskRunning = new AtomicBoolean(false);

    private final AtomicBoolean restartInProgress = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> reconnectTask;

    public WebsocketBrokerReconnectionService(@Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler,
            Optional<StompBrokerRelayMessageHandler> stompBrokerRelayMessageHandler) {
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.stompBrokerRelayMessageHandler = stompBrokerRelayMessageHandler;
    }

    @Override
    public void onApplicationEvent(BrokerAvailabilityEvent event) {
        if (stompBrokerRelayMessageHandler.isEmpty()) {
            return;
        }

        if (event.isBrokerAvailable()) {
            stopReconnectAttempts("broker became available again");
        }
        else {
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
        startReconnectAttempts("manual reconnect requested");
        return true;
    }

    private void startReconnectAttempts(String reason) {
        if (reconnectTaskRunning.compareAndSet(false, true)) {
            log.warn("Starting websocket broker reconnect attempts because {}", reason);
            restartBrokerRelay();
            reconnectTask = messageBrokerTaskScheduler.scheduleWithFixedDelay(this::restartBrokerRelay, Instant.now(), RECONNECT_INTERVAL);
        }
    }

    private void restartBrokerRelay() {
        if (!reconnectTaskRunning.get()) {
            return;
        }

        stompBrokerRelayMessageHandler.ifPresent(handler -> {
            if (!restartInProgress.compareAndSet(false, true)) {
                return; // Avoid overlapping restart attempts
            }

            try {
                if (handler.isRunning()) {
                    handler.stop();
                }
                handler.start();
            }
            catch (Exception ex) {
                log.warn("Failed to restart websocket broker relay: {}", ex.getMessage(), ex);
            }
            finally {
                restartInProgress.set(false);
            }
        });
    }

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
    }
}
