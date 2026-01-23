package de.tum.cit.aet.artemis.modeling.service.apollon;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.modeling.config.ApollonEnabled;

@Component
@Lazy
@Conditional(ApollonEnabled.class)
public class ApollonHealthIndicator implements HealthIndicator {

    private final RestTemplate shortTimeoutRestTemplate;

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    public ApollonHealthIndicator(@Qualifier("shortTimeoutApollonRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    /**
     * Ping Apollon at /status and check if the service is available.
     */
    @Override
    public Health health() {
        Map<String, Object> additionalInfo = Map.of("url", apollonConversionUrl);
        ConnectorHealth health;
        try {
            ResponseEntity<String> response = shortTimeoutRestTemplate.getForEntity(apollonConversionUrl + "/api/converter/status", String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            health = new ConnectorHealth(statusCode.is2xxSuccessful(), additionalInfo);
        }
        catch (RestClientException error) {
            health = new ConnectorHealth(error);
        }

        return health.asActuatorHealth();
    }
}
