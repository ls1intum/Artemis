package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;

@Component
@Profile("athena")
public class AthenaHealthIndicator implements HealthIndicator {

    private final RestTemplate shortTimeoutRestTemplate;

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    public AthenaHealthIndicator(@Qualifier("shortTimeoutAthenaRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    /**
     * Ping Athena at /queueStatus and check if the service is available.
     */
    @Override
    public Health health() {
        ConnectorHealth health;
        try {
            final var status = shortTimeoutRestTemplate.getForObject(athenaUrl + "/queueStatus", JsonNode.class);
            var isUp = status.get("total").isNumber();
            health = new ConnectorHealth(isUp);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
        }

        health.setAdditionalInfo(Map.of("url", athenaUrl));
        return health.asActuatorHealth();
    }
}
