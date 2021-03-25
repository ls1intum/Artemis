package de.tum.in.www1.artemis.service.connectors.apollon;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;

@Component
@Profile("apollon")
public class ApollonHealthIndicator implements HealthIndicator {

    private final RestTemplate shortTimeoutRestTemplate;

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    public ApollonHealthIndicator(@Qualifier("shortTimeoutApollonRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    /**
     * Ping Athene at /queueStatus and check if the service is available.
     */
    @Override
    public Health health() {
        ConnectorHealth health;
        try {
            ResponseEntity<String> response = shortTimeoutRestTemplate.getForEntity(apollonConversionUrl + "/status", String.class);
            HttpStatus statusCode = response.getStatusCode();
            health = new ConnectorHealth(statusCode.is2xxSuccessful());
        }
        catch (RestClientException error) {
            health = new ConnectorHealth(error);
        }

        health.setAdditionalInfo(Map.of("url", apollonConversionUrl));
        return health.asActuatorHealth();
    }
}
