package de.tum.in.www1.artemis.config.websocket;

import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.*;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;

@Component
public class WebsocketBrokerHealthIndicator implements HealthIndicator, ApplicationListener<BrokerAvailabilityEvent> {

    private boolean isBrokerAvailable = false; // Will be updated to true by event listener once connection is established

    // Split the addresses by comma
    @Value("#{'${spring.websocket.broker.addresses}'.split(',')}")
    private List<String> brokerAddresses;

    @Override
    public Health health() {
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("ipAddresses", brokerAddresses);
        return new ConnectorHealth(isBrokerAvailable, additionalInformation).asActuatorHealth();
    }

    @Override
    public void onApplicationEvent(BrokerAvailabilityEvent event) {
        // The event is fired if the broker gets (un-)available
        this.isBrokerAvailable = event.isBrokerAvailable();
    }
}
