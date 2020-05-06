package de.tum.in.www1.artemis.web.rest;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import io.github.jhipster.config.metric.JHipsterMetricsEndpoint;

@Component
@EndpointWebExtension(endpoint = JHipsterMetricsEndpoint.class)
public class CustomMetricsExtension {

    private final JHipsterMetricsEndpoint delegate;

    private final SimpUserRegistry simpUserRegistry;

    public CustomMetricsExtension(JHipsterMetricsEndpoint delegate, SimpUserRegistry simpUserRegistry) {
        this.delegate = delegate;
        this.simpUserRegistry = simpUserRegistry;
    }

    @ReadOperation
    public Map<String, Map> getMetrics() {
        Map<String, Map> metrics = this.delegate.allMetrics();
        HashMap<String, Integer> activeUsers = new HashMap<>();
        activeUsers.put("activeUsers", this.simpUserRegistry.getUserCount());
        metrics.put("customMetrics", new HashMap(activeUsers));
        return metrics;
    }

}
