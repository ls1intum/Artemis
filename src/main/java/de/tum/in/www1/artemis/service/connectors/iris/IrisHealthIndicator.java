package de.tum.in.www1.artemis.service.connectors.iris;

import java.net.URI;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;

@Component
@Profile("iris")
public class IrisHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;

    @Value("${artemis.iris.url}")
    private URI irisUrl;

    public IrisHealthIndicator(@Qualifier("shortTimeoutIrisRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Ping Iris at /health and check if the service is available and what its status is.
     */
    @Override
    public Health health() {
        // Disabled until PyrisV2 reimplements this
        return new ConnectorHealth(true).asActuatorHealth();
    }
}
