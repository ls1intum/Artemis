package de.tum.in.www1.artemis.service.connectors.athene;

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
@Profile("athene")
public class AtheneHealthIndicator implements HealthIndicator {

    private final RestTemplate shortTimeoutRestTemplate;

    @Value("${artemis.athene.url}")
    private String atheneUrl;

    public AtheneHealthIndicator(@Qualifier("shortTimeoutAtheneRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    @Override
    public Health health() {
        ConnectorHealth health;
        try {
            final var status = shortTimeoutRestTemplate.getForObject(atheneUrl + "/queueStatus", JsonNode.class);
            var isUp = status.get("total").isNumber();
            health = new ConnectorHealth(isUp);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
        }

        health.setAdditionalInfo(Map.of("url", atheneUrl));
        return health.asActuatorHealth();
    }
}
