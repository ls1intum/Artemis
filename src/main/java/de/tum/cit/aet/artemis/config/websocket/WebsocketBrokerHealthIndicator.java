package de.tum.cit.aet.artemis.config.websocket;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.service.connectors.ConnectorHealth;

@Profile(PROFILE_CORE)
@Component
public class WebsocketBrokerHealthIndicator implements HealthIndicator, ApplicationListener<BrokerAvailabilityEvent> {

    private boolean isBrokerAvailable = false; // Will be updated to true by event listener once connection is established

    // Split the addresses by comma
    @Value("#{'${spring.websocket.broker.addresses}'.split(',')}")
    private List<String> brokerAddresses;

    @Override
    public Health health() {
        Map<String, Object> additionalInformation = Map.of("ipAddresses", brokerAddresses);
        return new ConnectorHealth(isBrokerAvailable, additionalInformation, null).asActuatorHealth();
    }

    @Override
    public void onApplicationEvent(BrokerAvailabilityEvent event) {
        // The event is fired if the broker gets (un-)available
        this.isBrokerAvailable = event.isBrokerAvailable();
    }
}
