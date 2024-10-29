package de.tum.cit.aet.artemis.athena.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

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

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;

/**
 * Service determining the health of the Athena service and its assessment modules.
 */
@Component
@Profile(PROFILE_ATHENA)
public class AthenaHealthIndicator implements HealthIndicator {

    private static final String GREEN_CIRCLE = "\uD83D\uDFE2"; // unicode green circle 🟢

    private static final String RED_CIRCLE = "\uD83D\uDD34"; // unicode red circle 🔴

    private static final String ATHENA_URL_KEY = "url";

    private static final String ATHENA_STATUS_KEY = "status";

    private static final String ATHENA_MODULES_KEY = "modules";

    private static final String ATHENA_ASSESSMENT_MODULE_MANAGER_KEY = "assessment module manager";

    private static final String ATHENA_MODULE_URL_KEY = "url";

    private static final String ATHENA_MODULE_EXERCISE_TYPE_KEY = "type";

    private static final String ATHENA_MODULE_HEALTHY_KEY = "healthy";

    private final RestTemplate shortTimeoutRestTemplate;

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    public AthenaHealthIndicator(@Qualifier("shortTimeoutAthenaRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    private record AthenaModuleHealth(String exerciseType, boolean healthy, String url) {
    }

    private static String moduleHealthToString(AthenaModuleHealth moduleHealth) {
        var healthString = moduleHealth.healthy ? GREEN_CIRCLE : RED_CIRCLE;
        healthString += " " + moduleHealth.url + " (" + moduleHealth.exerciseType + ")";
        return healthString;
    }

    /**
     * Ping Athena at /health and check if the service is available.
     */
    @Override
    public Health health() {
        var additionalInfo = new HashMap<String, Object>();
        additionalInfo.put(ATHENA_URL_KEY, athenaUrl);
        ConnectorHealth health;
        try {
            final var response = shortTimeoutRestTemplate.getForObject(athenaUrl + "/health", JsonNode.class);
            final var athenaStatus = response != null ? response.get(ATHENA_STATUS_KEY).asText() : null;

            if (athenaStatus != null) {
                additionalInfo.put(ATHENA_ASSESSMENT_MODULE_MANAGER_KEY, athenaStatus);
                additionalInfo.putAll(getAdditionalInfoForModules(response));
            }
            else {
                additionalInfo.put(ATHENA_ASSESSMENT_MODULE_MANAGER_KEY, "not available");
            }
            health = new ConnectorHealth("ok".equals(athenaStatus), additionalInfo);
        }
        catch (Exception ex) {
            health = new ConnectorHealth(false, additionalInfo, ex);
        }

        return health.asActuatorHealth();
    }

    /**
     * Get additional information about the health of the assessment modules.
     */
    private Map<String, Object> getAdditionalInfoForModules(JsonNode athenaHealthResponse) {
        var additionalModuleInfo = new HashMap<String, Object>();
        if (athenaHealthResponse.has(ATHENA_MODULES_KEY)) {
            JsonNode modules = athenaHealthResponse.get(ATHENA_MODULES_KEY);
            // keys are module names, values are description maps
            modules.fields().forEachRemaining(module -> {
                var moduleHealth = new AthenaModuleHealth(module.getValue().get(ATHENA_MODULE_EXERCISE_TYPE_KEY).asText(),
                        module.getValue().get(ATHENA_MODULE_HEALTHY_KEY).asBoolean(), module.getValue().get(ATHENA_MODULE_URL_KEY).asText());
                additionalModuleInfo.put(module.getKey(), moduleHealthToString(moduleHealth));
            });
        }
        return additionalModuleInfo;
    }
}
