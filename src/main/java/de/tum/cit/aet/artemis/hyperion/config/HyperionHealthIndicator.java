package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.hyperion.client.api.HealthcheckApi;
import de.tum.cit.aet.artemis.hyperion.client.model.HealthCheck;

/**
 * Spring Boot health indicator for monitoring Hyperion service availability.
 */
@Component
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HyperionHealthIndicator.class);

    private static final String HYPERION_URL_KEY = "url";

    private static final String HYPERION_SECURITY_KEY = "security";

    private static final String HYPERION_STATUS_KEY = "status";

    private static final String HYPERION_VERSION_KEY = "version";

    private static final String HYPERION_UPTIME_KEY = "uptime";

    private final HealthcheckApi healthcheckApi;

    @Value("${artemis.hyperion.url}")
    private String hyperionUrl;

    @Value("${artemis.hyperion.api-key}")
    private String hyperionApiKey;

    public HyperionHealthIndicator(HealthcheckApi healthcheckApi) {
        this.healthcheckApi = healthcheckApi;
    }

    @Override
    public Health health() {
        var additionalInfo = new HashMap<String, Object>();
        additionalInfo.put(HYPERION_URL_KEY, hyperionUrl);
        additionalInfo.put(HYPERION_SECURITY_KEY, hyperionApiKey != null ? "API Key configured" : "No authentication");

        ConnectorHealth health;
        try {
            log.debug("Performing health check for Hyperion using generated API client");

            // Use the generated HealthcheckApi to perform health check
            HealthCheck healthCheckResponse = healthcheckApi.getHealthHealthGet();

            additionalInfo.put(HYPERION_STATUS_KEY, "UP");

            // Extract additional health information from the response
            if (healthCheckResponse != null) {
                if (healthCheckResponse.getVersion() != null) {
                    additionalInfo.put(HYPERION_VERSION_KEY, healthCheckResponse.getVersion());
                }
                if (healthCheckResponse.getUptimeSeconds() != null) {
                    additionalInfo.put(HYPERION_UPTIME_KEY, healthCheckResponse.getUptimeSeconds() + " seconds");
                }
                if (healthCheckResponse.getTimestamp() != null) {
                    additionalInfo.put("timestamp", healthCheckResponse.getTimestamp());
                }
            }

            health = new ConnectorHealth(true, additionalInfo);
            log.debug("Hyperion health check successful");

        }
        catch (Exception e) {
            log.warn("Hyperion health check failed: {}", e.getMessage());
            additionalInfo.put(HYPERION_STATUS_KEY, "DOWN");
            additionalInfo.put("error", e.getMessage());
            health = new ConnectorHealth(false, additionalInfo, e);
        }

        return health.asActuatorHealth();
    }
}
