package de.tum.cit.aet.artemis.core.config.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.CacheStats;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.DatabaseMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.GarbageCollectorMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.GaugeValue;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.HttpRequestMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.MemoryMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.MetricsResponse;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.ProcessMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.RequestCount;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.RequestStats;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.TimerSummary;
import de.tum.cit.aet.artemis.core.service.distributed.NodeRegistryService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

class NodeMetricsCollectorTest {

    private final ArtemisMetricsEndpoint endpoint = mock(ArtemisMetricsEndpoint.class);

    private final DistributedDataProvider provider = mock(DistributedDataProvider.class);

    private final NodeRegistryService registry = mock(NodeRegistryService.class);

    @SuppressWarnings("unchecked")
    private final DistributedMap<String, Map<String, Object>> distributedMap = mock(DistributedMap.class);

    private final Map<String, Map<String, Object>> snapshots = new LinkedHashMap<>();

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    private final NodeMetricsCollector collector = new NodeMetricsCollector(endpoint, provider, registry, mapper);

    @BeforeEach
    void setUp() {
        when(provider.<String, Map<String, Object>>getExpiringMap("nodeMetrics", Duration.ofSeconds(60))).thenReturn(distributedMap);
        when(distributedMap.values()).thenAnswer(_ -> snapshots.values());
        when(distributedMap.entrySet()).thenAnswer(_ -> snapshots.entrySet());
        when(distributedMap.get(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> snapshots.get(call.getArgument(0)));
    }

    @Test
    void publishesSerializableSnapshotWithNodeIdentityAndExpiry() {
        MetricsResponse metrics = emptyMetrics();
        when(registry.getLocalNodeId()).thenReturn("node-a");
        when(endpoint.allMetrics()).thenReturn(metrics);
        Instant beforePublish = Instant.now();
        collector.pushLocalMetrics();
        @SuppressWarnings("unchecked")
        var snapshot = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(distributedMap).put(org.mockito.ArgumentMatchers.eq("node-a"), snapshot.capture());
        NodeMetricsSnapshot decoded = mapper.convertValue(snapshot.getValue(), NodeMetricsSnapshot.class);
        assertThat(decoded.nodeId()).isEqualTo("node-a");
        assertThat(decoded.nodeLabel()).isEqualTo("node-a");
        assertThat(decoded.timestamp()).isBetween(beforePublish, Instant.now());
        assertThat(decoded.metrics()).isEqualTo(metrics);
    }

    @Test
    void ignoresMalformedSnapshotsAndFallsBackToLocalMetrics() {
        snapshots.put("broken", Map.of("timestamp", "not-a-timestamp"));
        MetricsResponse local = emptyMetrics();
        when(endpoint.allMetrics()).thenReturn(local);
        assertThat(collector.getAvailableNodes()).isEmpty();
        assertThat(collector.getMetricsForNode("broken")).isNull();
        assertThat(collector.getMetricsForNode("missing")).isNull();
        assertThat(collector.getAggregatedMetrics()).isSameAs(local);
        when(registry.getLocalNodeId()).thenThrow(new IllegalStateException("registry unavailable"));
        org.assertj.core.api.Assertions.assertThatCode(collector::pushLocalMetrics).doesNotThrowAnyException();
    }

    @Test
    void returnsSingleNodeWithoutAggregation() {
        MetricsResponse local = fullMetrics(1, 2, 10, 50);
        add("one", local);
        assertThat(collector.getAvailableNodes()).containsExactly(new NodeMetricsCollector.NodeInfo("one", "label-one"));
        assertThat(collector.getMetricsForNode("one")).isEqualTo(local);
        assertThat(collector.getAggregatedMetrics()).isEqualTo(local);
    }

    @Test
    void aggregatesCountersWeightedMeansMaximaAndEarliestStart() {
        add("one", fullMetrics(1, 2, 10, 50));
        add("two", fullMetrics(3, 6, 20, 100));
        add("empty", emptyMetrics());
        MetricsResponse aggregated = collector.getAggregatedMetrics();
        assertThat(aggregated.jvm()).containsEntry("heap", new MemoryMetrics(4, 8, 12));
        assertThat(aggregated.processMetrics()).isEqualTo(new ProcessMetrics(2, 8, 2, 2, 8, 12, 10, 100));
        assertThat(aggregated.garbageCollector()).isEqualTo(new GarbageCollectorMetrics(4, 4, 4, 4, 4, 4, timer(8, 2.5, 6)));
        assertThat(aggregated.httpServerRequests()).isEqualTo(new HttpRequestMetrics(new RequestCount(8), Map.of("200", new RequestStats(8, 2.5, 6))));
        assertThat(aggregated.cache()).containsEntry("users", new CacheStats(4, 4, 4, 4, 4, 4));
        assertThat(aggregated.databases()).isEqualTo(new DatabaseMetrics(new GaugeValue(4), new GaugeValue(4), new GaugeValue(4), new GaugeValue(4), new GaugeValue(4),
                new GaugeValue(4), timer(8, 2.5, 6), timer(8, 2.5, 6), timer(8, 2.5, 6)));
        assertThat(aggregated.services()).containsEntry("/api/courses", Map.of("GET", new RequestStats(8, 2.5, 6)));
    }

    @Test
    void zeroCountsAndMissingTimersDoNotProduceNaN() {
        TimerSummary zero = timer(0, 0, 0);
        var stats = new RequestStats(0, 0, 0);
        var gc = new GarbageCollectorMetrics(0, 0, 0, 0, 0, 0, zero);
        var process = new ProcessMetrics(0, 0, 0, 0, 0, 0, 0, 0);
        add("one", new MetricsResponse(null, process, gc, new HttpRequestMetrics(new RequestCount(0), Map.of("200", stats)), null, database(null),
                Map.of("/api/courses", Map.of("GET", stats))));
        add("two", new MetricsResponse(null, process, gc, new HttpRequestMetrics(new RequestCount(0), Map.of("200", stats)), null, database(zero),
                Map.of("/api/courses", Map.of("GET", stats))));
        add("three", new MetricsResponse(null, null, new GarbageCollectorMetrics(0, 0, 0, 0, 0, 0, null), new HttpRequestMetrics(new RequestCount(0), null), null, database(null),
                null));
        var aggregated = collector.getAggregatedMetrics();
        assertThat(aggregated.processMetrics().processStartTime()).isZero();
        assertThat(aggregated.garbageCollector().gcPause()).isEqualTo(zero);
        assertThat(aggregated.httpServerRequests().percode()).containsEntry("200", stats);
        assertThat(aggregated.services()).containsEntry("/api/courses", Map.of("GET", stats));
        assertThat(aggregated.databases().acquire()).isEqualTo(zero);
    }

    @SuppressWarnings("unchecked")
    private void add(String id, MetricsResponse metrics) {
        snapshots.put(id, mapper.convertValue(new NodeMetricsSnapshot(id, "label-" + id, Instant.EPOCH, metrics), Map.class));
    }

    private MetricsResponse emptyMetrics() {
        return new MetricsResponse(null, null, null, null, null, null, null);
    }

    private MetricsResponse fullMetrics(int factor, int count, int start, int uptime) {
        var timer = timer(count, factor, factor * 2);
        var stats = new RequestStats(count, factor, factor * 2);
        return new MetricsResponse(Map.of("heap", new MemoryMetrics(factor, factor * 2, factor * 3)),
                new ProcessMetrics(factor, factor * 2, factor, factor, factor * 2, factor * 3, start, uptime),
                new GarbageCollectorMetrics(factor, factor, factor, factor, factor, factor, timer), new HttpRequestMetrics(new RequestCount(count), Map.of("200", stats)),
                Map.of("users", new CacheStats(factor, factor, factor, factor, factor, factor)), new DatabaseMetrics(new GaugeValue(factor), new GaugeValue(factor),
                        new GaugeValue(factor), new GaugeValue(factor), new GaugeValue(factor), new GaugeValue(factor), timer, timer, timer),
                Map.of("/api/courses", Map.of("GET", stats)));
    }

    private DatabaseMetrics database(TimerSummary timer) {
        var zero = new GaugeValue(0);
        return new DatabaseMetrics(zero, zero, zero, zero, zero, zero, timer, timer, timer);
    }

    private TimerSummary timer(long count, double mean, double max) {
        return new TimerSummary(count, mean, max, count * mean, max, max, max, max, max, max);
    }
}
