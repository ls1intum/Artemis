package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint;
import de.tum.cit.aet.artemis.core.config.metric.NodeMetricsCollector;

/**
 * Extends the default Artemis metrics endpoint with multi-node aggregation
 * and active WebSocket user counts.
 * <p>
 * Supports node selection:
 * <ul>
 * <li>{@code GET /management/artemismetrics} — aggregated metrics from all nodes</li>
 * <li>{@code GET /management/artemismetrics/{nodeId}} — metrics for a specific node, or "nodes" to list available nodes</li>
 * </ul>
 */
@Component
@Lazy
@Profile(PROFILE_CORE)
@EndpointWebExtension(endpoint = ArtemisMetricsEndpoint.class)
public class CustomMetricsExtension {

    private final ArtemisMetricsEndpoint artemisMetricsEndpoint;

    private final NodeMetricsCollector nodeMetricsService;

    private final SimpUserRegistry simpUserRegistry;

    public CustomMetricsExtension(ArtemisMetricsEndpoint artemisMetricsEndpoint, NodeMetricsCollector nodeMetricsService, SimpUserRegistry simpUserRegistry) {
        this.artemisMetricsEndpoint = artemisMetricsEndpoint;
        this.nodeMetricsService = nodeMetricsService;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * Returns aggregated metrics from all cluster nodes (default view).
     *
     * @return aggregated metrics with active user count
     */
    @ReadOperation
    public Map<String, Object> getMetrics() {
        var metrics = new LinkedHashMap<>(nodeMetricsService.getAggregatedMetrics());
        metrics.put("customMetrics", Map.of("activeUsers", this.simpUserRegistry.getUserCount()));
        return metrics;
    }

    /**
     * Returns metrics for a specific node or the list of available nodes.
     * <p>
     * If {@code nodeId} is "nodes", returns the list of available cluster nodes.
     * Otherwise, returns the metrics snapshot for the specified node.
     *
     * @param nodeId the node UUID or "nodes" for the node list
     * @return node-specific metrics, or the node list
     */
    @ReadOperation
    public Object getMetricsByNode(@Selector @Nullable String nodeId) {
        if (nodeId == null || "all".equals(nodeId)) {
            return getMetrics();
        }
        if ("nodes".equals(nodeId)) {
            return nodeMetricsService.getAvailableNodes();
        }
        Map<String, Object> nodeMetrics = nodeMetricsService.getMetricsForNode(nodeId);
        if (nodeMetrics.isEmpty()) {
            return Map.of("error", "Node not found: " + nodeId);
        }
        return nodeMetrics;
    }
}
