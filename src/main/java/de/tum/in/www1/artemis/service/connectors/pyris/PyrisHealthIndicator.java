package de.tum.in.www1.artemis.service.connectors.pyris;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisHealthStatusDTO;

@Component
@Profile("iris")
public class PyrisHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;

    @Value("${artemis.iris.url}")
    private URI irisUrl;

    public PyrisHealthIndicator(@Qualifier("shortTimeoutPyrisRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Ping Iris at /health and check if the service is available and what its status is.
     */
    @Override
    public Health health() {
        ConnectorHealth health;
        try {
            PyrisHealthStatusDTO[] status = restTemplate.getForObject(irisUrl + "/api/v1/health", PyrisHealthStatusDTO[].class);
            var isUp = status != null && Arrays.stream(status).anyMatch(s -> s.status() == PyrisHealthStatusDTO.ModelStatus.UP);
            Map<String, Object> additionalInfo = Map.of("url", irisUrl, "modelStatuses", status);
            health = new ConnectorHealth(isUp, additionalInfo);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
            health.setUp(false);
            health.setAdditionalInfo(Map.of("url", irisUrl, "exception", emAll.getLocalizedMessage()));
        }

        return health.asActuatorHealth();
    }
}
