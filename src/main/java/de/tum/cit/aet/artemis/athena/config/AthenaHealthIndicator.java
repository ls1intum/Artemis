package de.tum.cit.aet.artemis.athena.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;

/**
 * Service determining the health of the Athena service and its assessment modules.
 */
@Component
@Lazy
@Conditional(AthenaEnabled.class)
public class AthenaHealthIndicator implements HealthIndicator {

    private static final String GREEN_CIRCLE = "\uD83D\uDFE2"; // unicode green circle 🟢

    private static final String RED_CIRCLE = "\uD83D\uDD34"; // unicode red circle 🔴

    private static final String ATHENA_URL_KEY = "url";

    private static final String ATHENA_ASSESSMENT_MODULE_MANAGER_KEY = "assessment module manager";

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
            final var healthResponse = shortTimeoutRestTemplate.getForObject(athenaUrl + "/health", AthenaHealthResponse.class);
            final var athenaStatus = healthResponse != null ? healthResponse.status() : null;

            if (athenaStatus != null) {
                additionalInfo.put(ATHENA_ASSESSMENT_MODULE_MANAGER_KEY, athenaStatus);
                additionalInfo.putAll(getAdditionalInfoForModules(healthResponse));
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
    private Map<String, Object> getAdditionalInfoForModules(AthenaHealthResponse athenaHealthResponse) {
        var additionalModuleInfo = new HashMap<String, Object>();
        var modules = athenaHealthResponse.modules();
        modules.forEach((moduleName, descriptionMap) -> {
            var moduleHealth = new AthenaModuleHealth(descriptionMap.type(), descriptionMap.healthy(), descriptionMap.url());
            additionalModuleInfo.put(moduleName, moduleHealthToString(moduleHealth));
        });
        return additionalModuleInfo;
    }
}
