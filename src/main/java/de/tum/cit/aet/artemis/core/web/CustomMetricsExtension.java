package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.MetricsResponse;
import de.tum.cit.aet.artemis.core.config.metric.NodeMetricsCollector;

/**
 * Extends the default Artemis metrics endpoint with multi-node aggregation
 * and active WebSocket user counts.
 * <ul>
 * <li>{@code GET /management/artemismetrics} — aggregated metrics from all nodes</li>
 * <li>{@code GET /management/artemismetrics/nodes} — list available cluster nodes</li>
 * <li>{@code GET /management/artemismetrics/{nodeId}} — metrics for a specific node</li>
 * </ul>
 */
@Component
@Lazy
@Profile(PROFILE_CORE)
@EndpointWebExtension(endpoint = ArtemisMetricsEndpoint.class)
public class CustomMetricsExtension {

    private final NodeMetricsCollector nodeMetricsCollector;

    private final SimpUserRegistry simpUserRegistry;

    public CustomMetricsExtension(NodeMetricsCollector nodeMetricsCollector, SimpUserRegistry simpUserRegistry) {
        this.nodeMetricsCollector = nodeMetricsCollector;
        this.simpUserRegistry = simpUserRegistry;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExtendedMetricsResponse(@JsonUnwrapped MetricsResponse metrics, CustomMetrics customMetrics) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CustomMetrics(int activeUsers) {
    }

    @ReadOperation
    public ExtendedMetricsResponse getMetrics() {
        var metrics = nodeMetricsCollector.getAggregatedMetrics();
        return new ExtendedMetricsResponse(metrics, new CustomMetrics(simpUserRegistry.getUserCount()));
    }

    /**
     * Returns metrics for a specific node or the list of available nodes.
     *
     * @param nodeId the node UUID, "nodes" for the node list, or "all" for aggregated metrics
     * @return node-specific metrics, the node list, or aggregated metrics
     */
    @ReadOperation
    public Object getMetricsByNode(@Selector @Nullable String nodeId) {
        if (nodeId == null || "all".equals(nodeId)) {
            return getMetrics();
        }
        if ("nodes".equals(nodeId)) {
            return nodeMetricsCollector.getAvailableNodes();
        }
        MetricsResponse nodeMetrics = nodeMetricsCollector.getMetricsForNode(nodeId);
        if (nodeMetrics == null) {
            return Map.of("error", "Node not found: " + nodeId);
        }
        return new ExtendedMetricsResponse(nodeMetrics, new CustomMetrics(simpUserRegistry.getUserCount()));
    }
}
