package de.tum.in.www1.artemis.web.rest;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import tech.jhipster.config.metric.JHipsterMetricsEndpoint;

/**
 * CustomMetricsExtension.
 * Extends the default JHI Metrics with custom metrics for Artemis.
 */
@Component
@EndpointWebExtension(endpoint = JHipsterMetricsEndpoint.class)
public class CustomMetricsExtension {

    private final JHipsterMetricsEndpoint jHipsterMetricsEndpoint;

    private final SimpUserRegistry simpUserRegistry;

    public CustomMetricsExtension(JHipsterMetricsEndpoint jHipsterMetricsEndpoint, SimpUserRegistry simpUserRegistry) {
        this.jHipsterMetricsEndpoint = jHipsterMetricsEndpoint;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * Expands the jhimetrics call with number of active users.
     *
     * @return extended jhimetrics
     */
    @ReadOperation
    public Map<String, Map<?, ?>> getMetrics() {
        var metrics = this.jHipsterMetricsEndpoint.allMetrics();
        HashMap<String, Integer> activeUsers = new HashMap<>();
        activeUsers.put("activeUsers", this.simpUserRegistry.getUserCount());
        metrics.put("customMetrics", new HashMap<>(activeUsers));
        return metrics;
    }

}
