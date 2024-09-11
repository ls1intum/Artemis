package de.tum.cit.aet.artemis.iris.service.pyris;

import java.net.URI;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisHealthStatusDTO;

@Component
@Profile("iris")
public class PyrisHealthIndicator implements HealthIndicator {

    @Value("${artemis.iris.health-ttl:30000}")
    private int CACHE_TTL;

    private final RestTemplate restTemplate;

    @Value("${artemis.iris.url}")
    private URI irisUrl;

    private long lastUpdated = 0;

    private Health cachedHealth = null;

    public PyrisHealthIndicator(@Qualifier("shortTimeoutPyrisRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Pings Iris at /health and checks if the service is available and what its status is.
     * Always uses a new ping and does not use a cached result.
     *
     * @return The health status of Iris
     */
    @Override
    public Health health() {
        return health(false);
    }

    /**
     * Ping Iris at /health and check if the service is available and what its status is.
     * Offers an option to use a cached result to avoid spamming the service.
     *
     * @param useCache Whether to use the cached result or not
     * @return The (cached) health status of Iris
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
