package de.tum.in.www1.artemis.service.connectors.iris;

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
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisStatusDTO;

@Component
@Profile("iris")
public class IrisHealthIndicator implements HealthIndicator {

    private final RestTemplate shortTimeoutRestTemplate;

    @Value("${artemis.iris.url}")
    private URI irisUrl;

    public IrisHealthIndicator(@Qualifier("shortTimeoutIrisRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    /**
     * Ping Iris at /health and check if the service is available and what its status is.
     */
    @Override
    public Health health() {
        ConnectorHealth health;
        try {
            final var status = shortTimeoutRestTemplate.getForObject(irisUrl.resolve("/api/v1/health"), IrisStatusDTO[].class);
            var isUp = status != null && Arrays.stream(status).anyMatch(s -> s.status() == IrisStatusDTO.ModelStatus.UP);
            Map<String, Object> additionalInfo = Map.of("url", irisUrl, "modelStatuses", status);
            health = new ConnectorHealth(isUp, additionalInfo);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
            health.setUp(false);
            health.setAdditionalInfo(Map.of("url", irisUrl));
        }

        return health.asActuatorHealth();
    }
}
