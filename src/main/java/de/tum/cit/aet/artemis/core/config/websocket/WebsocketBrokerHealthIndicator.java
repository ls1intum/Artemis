package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;

@Profile(PROFILE_CORE)
@Component
@Lazy
public class WebsocketBrokerHealthIndicator implements HealthIndicator, ApplicationListener<BrokerAvailabilityEvent> {

    private static final Logger log = LoggerFactory.getLogger(WebsocketBrokerHealthIndicator.class);

    private final Optional<StompBrokerRelayMessageHandler> stompBrokerRelayMessageHandler;

    private boolean isBrokerAvailable = false; // Will be updated to true by event listener once connection is established

    // Split the addresses by comma
    @Value("#{'${spring.websocket.broker.addresses}'.split(',')}")
    private List<String> brokerAddresses;

    public WebsocketBrokerHealthIndicator(Optional<StompBrokerRelayMessageHandler> stompBrokerRelayMessageHandler) {
        this.stompBrokerRelayMessageHandler = stompBrokerRelayMessageHandler;
    }

    @Override
    public Health health() {
        boolean isRunning = stompBrokerRelayMessageHandler.map(StompBrokerRelayMessageHandler::isRunning).orElse(true);
        Map<String, Object> additionalInformation = Map.of("ipAddresses", brokerAddresses, "isRunning", isRunning, "isBrokerAvailable", isBrokerAvailable);
        return new ConnectorHealth(isBrokerAvailable && isRunning, additionalInformation, null).asActuatorHealth();
    }

    @Override
    public void onApplicationEvent(BrokerAvailabilityEvent event) {
        if (event.isBrokerAvailable()) {
            log.info("Websocket broker is now available.");
        }
        else {
            log.warn("Websocket broker is now unavailable.");
        }
        // The event is fired if the broker gets (un-)available
        this.isBrokerAvailable = event.isBrokerAvailable();
    }
}
