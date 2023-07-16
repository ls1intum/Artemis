package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.HashMap;
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

/**
 * Service determining the health of the Athena service and its assessment modules.
 */
@Component
@Profile("athena")
public class AthenaHealthIndicator implements HealthIndicator {

    private static final String GREEN_CIRCLE = "\uD83D\uDFE2"; // unicode green circle

    private static final String RED_CIRCLE = "\uD83D\uDD34"; // unicode red circle

    private final RestTemplate shortTimeoutRestTemplate;

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    public AthenaHealthIndicator(@Qualifier("shortTimeoutAthenaRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    private record AthenaModuleHealth(String type, boolean healthy, String url) {
    }

    private static String moduleHealthToString(AthenaModuleHealth moduleHealth) {
        var healthString = "";
        if (moduleHealth.healthy) {
            healthString += GREEN_CIRCLE; // green circle
        }
        else {
            healthString += RED_CIRCLE; // red circle
        }
        healthString += " " + moduleHealth.url + " (" + moduleHealth.type + ")";
        return healthString;
    }

    /**
     * Ping Athena at /health and check if the service is available.
     */
    @Override
    public Health health() {
        ConnectorHealth health;
        try {
            final var response = shortTimeoutRestTemplate.getForObject(athenaUrl + "/health", JsonNode.class);
            var isUp = response != null && response.get("status").asText().equals("ok");
            health = new ConnectorHealth(isUp);
            var additionalInfo = new HashMap<String, Object>();
            additionalInfo.put("url", athenaUrl);
            if (response != null) {
                additionalInfo.put("assessment module manager", response.get("status").asText());
                additionalInfo.putAll(getAdditionalInfoForModules(response));
            }
            else {
                additionalInfo.put("assessment module manager", "not available");
            }
            health.setAdditionalInfo(additionalInfo);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
            health.setAdditionalInfo(Map.of("url", athenaUrl));
        }

        return health.asActuatorHealth();
    }

    /**
     * Get additional information about the health of the assessment modules.
     */
    private Map<String, Object> getAdditionalInfoForModules(JsonNode athenaHealthResponse) {
        var additionalModuleInfo = new HashMap<String, Object>();
        if (athenaHealthResponse.has("modules")) {
            JsonNode modules = athenaHealthResponse.get("modules");
            // keys are module names, values are description maps
            modules.fields().forEachRemaining(module -> {
                var moduleHealth = new AthenaModuleHealth(module.getValue().get("type").asText(), module.getValue().get("healthy").asBoolean(),
                        module.getValue().get("url").asText());
                additionalModuleInfo.put(module.getKey(), moduleHealthToString(moduleHealth));
            });
        }
        return additionalModuleInfo;
    }
}
