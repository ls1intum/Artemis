package de.tum.cit.aet.artemis.communication.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;

/**
 * Service determining the health of the Hermes push notification service.
 */
@Profile(PROFILE_CORE)
@Component
public class HermesHealthIndicator implements HealthIndicator {

    private final RestTemplate shortTimeoutRestTemplate;

    @Value("${artemis.push-notification-relay:https://hermes-sandbox.artemis.cit.tum.de}")
    private String hermesUrl;

    public HermesHealthIndicator(@Qualifier("shortTimeoutHermesRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    /**
     * Ping Hermes and check if the service is available.
     */
    @Override
    public Health health() {
        var additionalInfo = new HashMap<String, Object>();
        additionalInfo.put("url", hermesUrl);
        ConnectorHealth health;
        try {
            ResponseEntity<String> response = shortTimeoutRestTemplate.getForEntity(hermesUrl, String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            health = new ConnectorHealth(statusCode.is2xxSuccessful(), additionalInfo);
        }
        catch (RestClientException error) {
            health = new ConnectorHealth(error);
        }

        return health.asActuatorHealth();
    }
}
