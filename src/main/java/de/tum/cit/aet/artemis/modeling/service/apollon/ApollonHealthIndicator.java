package de.tum.cit.aet.artemis.modeling.service.apollon;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_APOLLON;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;

@Component
@Profile(PROFILE_APOLLON)
public class ApollonHealthIndicator implements HealthIndicator {

    private final RestClient shortTimeoutRestClient;

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    public ApollonHealthIndicator(@Qualifier("shortTimeoutApollonRestClient") RestClient shortTimeoutRestClient) {
        this.shortTimeoutRestClient = shortTimeoutRestClient;
    }

    /**
     * Ping Apollon at /status and check if the service is available.
     */
    @Override
    public Health health() {
        Map<String, Object> additionalInfo = Map.of("url", apollonConversionUrl);
        ConnectorHealth health;
        try {
            ResponseEntity<String> response = shortTimeoutRestClient.get().uri(apollonConversionUrl + "/status").retrieve().toEntity(String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            health = new ConnectorHealth(statusCode.is2xxSuccessful(), additionalInfo);
        }
        catch (RestClientException error) {
            health = new ConnectorHealth(error);
        }

        return health.asActuatorHealth();
    }
}
