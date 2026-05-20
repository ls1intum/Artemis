package de.tum.cit.aet.artemis.core.config.metric;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

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

/**
 * Periodically collects local metrics and stores them in a shared Hazelcast IMap
 * so that any node in the cluster can aggregate or display per-node metrics.
 * <p>
 * Each node pushes a snapshot every 15 seconds. Stale entries (from nodes that
 * left the cluster) are evicted automatically via the IMap TTL (60 seconds).
 * <p>
 * Note: Uses HazelcastInstance directly (the DistributedDataProvider abstraction is
 * scoped to LocalCI/BuildAgent profiles). When migrating to Redis, replace the
 * {@link #getMap()}, {@link #getLocalNodeId()}, and {@link #getLocalNodeLabel()} methods.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy(false)
public class NodeMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(NodeMetricsCollector.class);

    private static final String MAP_NAME = "nodeMetrics";

    private final ArtemisMetricsEndpoint metricsEndpoint;

    private final HazelcastInstance hazelcastInstance;

    private final ObjectMapper objectMapper;

    public NodeMetricsCollector(ArtemisMetricsEndpoint metricsEndpoint, HazelcastInstance hazelcastInstance, ObjectMapper objectMapper) {
        this.metricsEndpoint = metricsEndpoint;
        this.hazelcastInstance = hazelcastInstance;
        this.objectMapper = objectMapper;
    }

    // --- Scheduled push ---

    /**
     * Pushes the local node's metrics snapshot to a shared Hazelcast map so other nodes can read it.
     */
    @Scheduled(initialDelay = 5_000, fixedRate = 15_000)
    public void pushLocalMetrics() {
        try {
            Member localMember = hazelcastInstance.getCluster().getLocalMember();
            String nodeId = localMember.getUuid().toString();
            String label = localMember.getAddress().getHost() + ":" + localMember.getAddress().getPort();

            // Store as a Map in Hazelcast for cross-node serialization compatibility
            var snapshot = new NodeMetricsSnapshot(nodeId, label, Instant.now(), metricsEndpoint.allMetrics());
            @SuppressWarnings("unchecked")
            Map<String, Object> serialized = objectMapper.convertValue(snapshot, Map.class);
            getMap().put(nodeId, serialized);
        }
        catch (Exception e) {
            log.warn("Failed to push local metrics to Hazelcast: {}", e.getMessage());
        }
    }

    // --- Public API ---

    /**
     * Returns a list of all cluster nodes that have recently pushed metrics.
     *
     * @return the available node identifiers and labels
     */
    public List<NodeInfo> getAvailableNodes() {
        List<NodeInfo> nodes = new ArrayList<>();
        for (var entry : getMap().entrySet()) {
            var snapshot = deserialize(entry.getValue());
            if (snapshot != null) {
                nodes.add(new NodeInfo(snapshot.nodeId(), snapshot.nodeLabel()));
            }
        }
        return nodes;
    }

    /**
     * Returns the latest metrics snapshot for a specific cluster node.
     *
     * @param nodeId the Hazelcast member UUID of the node
     * @return the metrics response, or null if the node is not found
     */
    public MetricsResponse getMetricsForNode(String nodeId) {
        var raw = getMap().get(nodeId);
        if (raw == null) {
            return null;
        }
        var snapshot = deserialize(raw);
        return snapshot != null ? snapshot.metrics() : null;
    }

    /**
     * Aggregates metrics from all cluster nodes into a single combined response.
     *
     * @return the aggregated metrics, or the local node's metrics if no cluster data is available
     */
    public MetricsResponse getAggregatedMetrics() {
        List<MetricsResponse> all = new ArrayList<>();
        for (var raw : getMap().values()) {
            var snapshot = deserialize(raw);
            if (snapshot != null) {
                all.add(snapshot.metrics());
            }
        }
        if (all.isEmpty()) {
            return metricsEndpoint.allMetrics();
        }
        if (all.size() == 1) {
            return all.getFirst();
        }
        return aggregate(all);
    }

    // --- Aggregation ---

    private MetricsResponse aggregate(List<MetricsResponse> nodes) {
        return new MetricsResponse(aggregateJvm(nodes), aggregateProcess(nodes), aggregateGc(nodes), aggregateHttp(nodes), aggregateCache(nodes), aggregateDatabases(nodes),
                aggregateServices(nodes));
    }

    private Map<String, MemoryMetrics> aggregateJvm(List<MetricsResponse> nodes) {
        Map<String, MemoryMetrics> result = new LinkedHashMap<>();
        for (var node : nodes) {
            if (node.jvm() == null) {
                continue;
            }
            for (var entry : node.jvm().entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), (a, b) -> new MemoryMetrics(a.committed() + b.committed(), a.max() + b.max(), a.used() + b.used()));
            }
        }
        return result;
    }

    private ProcessMetrics aggregateProcess(List<MetricsResponse> nodes) {
        double cpuUsageSum = 0, cpuCountSum = 0, loadSum = 0, procCpuSum = 0, filesMaxSum = 0, filesOpenSum = 0;
        double minStart = Double.MAX_VALUE, maxUptime = 0;
        int count = 0;
        for (var node : nodes) {
            var pm = node.processMetrics();
            if (pm == null) {
                continue;
            }
            count++;
            cpuUsageSum += pm.systemCpuUsage();
            cpuCountSum += pm.systemCpuCount();
            loadSum += pm.systemLoadAverage();
            procCpuSum += pm.processCpuUsage();
            filesMaxSum += pm.processFilesMax();
            filesOpenSum += pm.processFilesOpen();
            if (pm.processStartTime() > 0 && pm.processStartTime() < minStart) {
                minStart = pm.processStartTime();
            }
            maxUptime = Math.max(maxUptime, pm.processUptime());
        }
        int n = Math.max(count, 1);
        return new ProcessMetrics(cpuUsageSum / n, cpuCountSum, loadSum / n, procCpuSum / n, filesMaxSum, filesOpenSum, minStart == Double.MAX_VALUE ? 0 : minStart, maxUptime);
    }

    private GarbageCollectorMetrics aggregateGc(List<MetricsResponse> nodes) {
        double liveSum = 0, maxSum = 0, promSum = 0, allocSum = 0, loadedSum = 0, unloadedSum = 0;
        TimerSummary pauseAgg = TimerSummary.EMPTY;
        for (var node : nodes) {
            var gc = node.garbageCollector();
            if (gc == null) {
                continue;
            }
            liveSum += gc.liveDataSize();
            maxSum += gc.maxDataSize();
            promSum += gc.memoryPromoted();
            allocSum += gc.memoryAllocated();
            loadedSum += gc.classesLoaded();
            unloadedSum += gc.classesUnloaded();
            pauseAgg = mergeTimers(pauseAgg, gc.gcPause());
        }
        return new GarbageCollectorMetrics(liveSum, maxSum, promSum, allocSum, loadedSum, unloadedSum, pauseAgg);
    }

    private HttpRequestMetrics aggregateHttp(List<MetricsResponse> nodes) {
        long totalCount = 0;
        Map<String, RequestStats> perCode = new TreeMap<>();
        for (var node : nodes) {
            var http = node.httpServerRequests();
            if (http == null) {
                continue;
            }
            totalCount += http.all().count();
            if (http.percode() != null) {
                for (var entry : http.percode().entrySet()) {
                    perCode.merge(entry.getKey(), entry.getValue(), NodeMetricsCollector::mergeStats);
                }
            }
        }
        return new HttpRequestMetrics(new RequestCount(totalCount), perCode);
    }

    private Map<String, CacheStats> aggregateCache(List<MetricsResponse> nodes) {
        Map<String, CacheStats> result = new TreeMap<>();
        for (var node : nodes) {
            if (node.cache() == null) {
                continue;
            }
            for (var entry : node.cache().entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), (a, b) -> new CacheStats(a.hits() + b.hits(), a.misses() + b.misses(), a.puts() + b.puts(),
                        a.evictions() + b.evictions(), a.removals() + b.removals(), a.size() + b.size()));
            }
        }
        return result;
    }

    private DatabaseMetrics aggregateDatabases(List<MetricsResponse> nodes) {
        double minSum = 0, maxSum = 0, idleSum = 0, activeSum = 0, pendingSum = 0, connSum = 0;
        TimerSummary acquireAgg = TimerSummary.EMPTY, creationAgg = TimerSummary.EMPTY, usageAgg = TimerSummary.EMPTY;
        for (var node : nodes) {
            var db = node.databases();
            if (db == null) {
                continue;
            }
            minSum += db.min().value();
            maxSum += db.max().value();
            idleSum += db.idle().value();
            activeSum += db.active().value();
            pendingSum += db.pending().value();
            connSum += db.connections().value();
            acquireAgg = mergeTimers(acquireAgg, db.acquire());
            creationAgg = mergeTimers(creationAgg, db.creation());
            usageAgg = mergeTimers(usageAgg, db.usage());
        }
        return new DatabaseMetrics(new GaugeValue(minSum), new GaugeValue(maxSum), new GaugeValue(idleSum), new GaugeValue(activeSum), new GaugeValue(pendingSum),
                new GaugeValue(connSum), acquireAgg, creationAgg, usageAgg);
    }

    private Map<String, Map<String, RequestStats>> aggregateServices(List<MetricsResponse> nodes) {
        Map<String, Map<String, RequestStats>> result = new TreeMap<>();
        for (var node : nodes) {
            if (node.services() == null) {
                continue;
            }
            for (var uriEntry : node.services().entrySet()) {
                result.computeIfAbsent(uriEntry.getKey(), k -> new LinkedHashMap<>());
                for (var methodEntry : uriEntry.getValue().entrySet()) {
                    result.get(uriEntry.getKey()).merge(methodEntry.getKey(), methodEntry.getValue(), NodeMetricsCollector::mergeStats);
                }
            }
        }
        return result;
    }

    // --- Helpers ---

    private static RequestStats mergeStats(RequestStats a, RequestStats b) {
        long total = a.count() + b.count();
        double mean = total > 0 ? (a.mean() * a.count() + b.mean() * b.count()) / total : 0;
        return new RequestStats(total, mean, Math.max(a.max(), b.max()));
    }

    private static TimerSummary mergeTimers(TimerSummary a, TimerSummary b) {
        if (a == null || a == TimerSummary.EMPTY) {
            return b != null ? b : TimerSummary.EMPTY;
        }
        if (b == null || b == TimerSummary.EMPTY) {
            return a;
        }
        long total = a.count() + b.count();
        double mean = total > 0 ? (a.mean() * a.count() + b.mean() * b.count()) / total : 0;
        return new TimerSummary(total, mean, Math.max(a.max(), b.max()), a.totalTime() + b.totalTime(), Math.max(a.p0(), b.p0()), Math.max(a.p50(), b.p50()),
                Math.max(a.p75(), b.p75()), Math.max(a.p95(), b.p95()), Math.max(a.p99(), b.p99()), Math.max(a.p100(), b.p100()));
    }

    private NodeMetricsSnapshot deserialize(Map<String, Object> raw) {
        try {
            return objectMapper.convertValue(raw, NodeMetricsSnapshot.class);
        }
        catch (Exception e) {
            log.debug("Could not deserialize node metrics snapshot: {}", e.getMessage());
            return null;
        }
    }

    private IMap<String, Map<String, Object>> getMap() {
        return hazelcastInstance.getMap(MAP_NAME);
    }

    public record NodeInfo(String nodeId, String label) {
    }
}
