package de.tum.cit.aet.artemis.core.config.metric;

import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.MetricsResponse;

/**
 * Snapshot of a single node's metrics, stored in Hazelcast IMap for multi-node aggregation.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NodeMetricsSnapshot(String nodeId, String nodeLabel, Instant timestamp, MetricsResponse metrics) implements Serializable {
}
