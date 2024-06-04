package de.tum.in.www1.artemis.service.connectors.pyris;

import java.net.URI;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisHealthStatusDTO;

@Component
@Profile("iris")
public class PyrisHealthIndicator implements HealthIndicator {

    private static final int CACHE_TTL = 30_000;

    private final RestTemplate restTemplate;

    @Value("${artemis.iris.url}")
    private URI irisUrl;

    private long lastUpdated = 0;

    private Health cachedHealth = null;

    public PyrisHealthIndicator(@Qualifier("shortTimeoutPyrisRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /*
     * Used by the MetricsBean - will always freshly update.
     */
    @Override
    public Health health() {
        return health(false);
    }

    /**
     * Ping Iris at /health and check if the service is available and what its status is.
     * Offers an option to use a cached result to avoid spamming the service.
     */
    public Health health(boolean useCache) {
        if (useCache && cachedHealth != null && System.currentTimeMillis() - lastUpdated < CACHE_TTL) {
            return cachedHealth;
        }

        ConnectorHealth health;
        try {
            PyrisHealthStatusDTO[] status = restTemplate.getForObject(irisUrl + "/api/v1/health/", PyrisHealthStatusDTO[].class);
            var isUp = status != null;
            health = new ConnectorHealth(isUp, null, null);
        }
        catch (RestClientException e) {
            health = new ConnectorHealth(false, null, e);
        }

        var newHealth = health.asActuatorHealth();
        cachedHealth = newHealth;
        lastUpdated = System.currentTimeMillis();

        return newHealth;
    }
}
