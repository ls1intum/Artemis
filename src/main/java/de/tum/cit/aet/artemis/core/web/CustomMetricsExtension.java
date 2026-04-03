package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint;

/**
 * Extends the default Artemis metrics endpoint with custom metrics
 * (e.g., active WebSocket user count).
 */
@Component
@Lazy
@Profile(PROFILE_CORE)
@EndpointWebExtension(endpoint = ArtemisMetricsEndpoint.class)
public class CustomMetricsExtension {

    private final ArtemisMetricsEndpoint artemisMetricsEndpoint;

    private final SimpUserRegistry simpUserRegistry;

    public CustomMetricsExtension(ArtemisMetricsEndpoint artemisMetricsEndpoint, SimpUserRegistry simpUserRegistry) {
        this.artemisMetricsEndpoint = artemisMetricsEndpoint;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * Expands the metrics call with number of active users.
     *
     * @return extended metrics
     */
    @ReadOperation
    public Map<String, Object> getMetrics() {
        var metrics = this.artemisMetricsEndpoint.allMetrics();
        metrics.put("customMetrics", Map.of("activeUsers", this.simpUserRegistry.getUserCount()));
        return metrics;
    }
}
