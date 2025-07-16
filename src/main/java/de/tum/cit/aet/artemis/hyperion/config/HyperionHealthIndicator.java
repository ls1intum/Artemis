package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;

/**
 * Health indicator for Hyperion service availability monitoring.
 *
 * Performs HTTP health checks against Hyperion service and reports
 * connection status, configuration details, and error information
 * for operational monitoring and troubleshooting.
 */
@Component
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HyperionHealthIndicator.class);

    private static final String HYPERION_URL_KEY = "url";

    private static final String HYPERION_SECURITY_KEY = "security";

    private static final String HYPERION_STATUS_KEY = "status";

    private final RestClient shortTimeoutRestClient;

    private final HyperionRestConfigurationProperties config;

    public HyperionHealthIndicator(@Qualifier("shortTimeoutHyperionRestClient") RestClient shortTimeoutRestClient, HyperionRestConfigurationProperties config) {
        this.shortTimeoutRestClient = shortTimeoutRestClient;
        this.config = config;
    }

    @Override
    public Health health() {
        var additionalInfo = new HashMap<String, Object>();
        additionalInfo.put(HYPERION_URL_KEY, config.getUrl());
        additionalInfo.put(HYPERION_SECURITY_KEY, config.getApiKey() != null ? "API Key configured" : "No authentication");

        ConnectorHealth health;
        try {
            log.debug("Performing REST health check for Hyperion at {}", config.getUrl());

            // Call the health endpoint using modern RestClient
            shortTimeoutRestClient.get().uri("/health").retrieve().body(Object.class);

            additionalInfo.put(HYPERION_STATUS_KEY, "UP");
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
