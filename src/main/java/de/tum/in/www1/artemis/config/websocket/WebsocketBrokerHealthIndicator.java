package de.tum.in.www1.artemis.config.websocket;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;

@Component
public class WebsocketBrokerHealthIndicator implements HealthIndicator, ApplicationListener<BrokerAvailabilityEvent> {

    private boolean isBrokerAvailable = false; // Will be updated to true by event listener once connection is established

    @Override
    public Health health() {
        return new ConnectorHealth(isBrokerAvailable).asActuatorHealth();
    }

    @Override
    public void onApplicationEvent(BrokerAvailabilityEvent event) {
        // The event is fired if the broker gets (un-)available
        this.isBrokerAvailable = event.isBrokerAvailable();
    }
}
