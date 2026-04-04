package de.tum.cit.aet.artemis.core.config.metric;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

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
@Lazy(false) // must start immediately for scheduled task
public class NodeMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(NodeMetricsCollector.class);

    private static final String MAP_NAME = "nodeMetrics";

    private static final String META_NODE_ID = "_nodeId";

    private static final String META_NODE_LABEL = "_nodeLabel";

    private static final String META_TIMESTAMP = "_timestamp";

    private final ArtemisMetricsEndpoint metricsEndpoint;

    private final HazelcastInstance hazelcastInstance;

    public NodeMetricsCollector(ArtemisMetricsEndpoint metricsEndpoint, HazelcastInstance hazelcastInstance) {
        this.metricsEndpoint = metricsEndpoint;
        this.hazelcastInstance = hazelcastInstance;
    }

    // --- Scheduled push ---

    /**
     * Pushes the local node's metrics into the shared Hazelcast IMap every 15 seconds.
     */
    @Scheduled(initialDelay = 5_000, fixedRate = 15_000)
    public void pushLocalMetrics() {
        try {
            Member localMember = hazelcastInstance.getCluster().getLocalMember();
            String nodeId = localMember.getUuid().toString();

            Map<String, Object> snapshot = new LinkedHashMap<>(metricsEndpoint.allMetrics());
            snapshot.put(META_NODE_ID, nodeId);
            snapshot.put(META_NODE_LABEL, formatNodeLabel(localMember));
            snapshot.put(META_TIMESTAMP, Instant.now().toEpochMilli());

            getMap().put(nodeId, snapshot);
        }
        catch (Exception e) {
            log.warn("Failed to push local metrics to Hazelcast: {}", e.getMessage());
        }
    }

    // --- Public API ---

    /**
     * Returns a list of all nodes that have recently pushed metrics.
     *
     * @return list of node descriptors with id and display label
     */
    public List<Map<String, String>> getAvailableNodes() {
        List<Map<String, String>> nodes = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : getMap().entrySet()) {
            Map<String, Object> snapshot = entry.getValue();
            nodes.add(Map.of("nodeId", entry.getKey(), "label", String.valueOf(snapshot.getOrDefault(META_NODE_LABEL, entry.getKey()))));
        }
        return nodes;
    }

    /**
     * Returns the metrics snapshot for a specific node.
     *
     * @param nodeId the Hazelcast member UUID
     * @return the metrics map, or an empty map if the node is not found
     */
    public Map<String, Object> getMetricsForNode(String nodeId) {
        Map<String, Object> snapshot = getMap().get(nodeId);
        return snapshot != null ? snapshot : Map.of();
    }

    /**
     * Aggregates metrics from all nodes in the cluster.
     * Sums counters, computes weighted averages for latencies,
     * and takes max/min where appropriate.
     *
     * @return the aggregated metrics in the same format as a single-node response
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAggregatedMetrics() {
        Collection<Map<String, Object>> allSnapshots = getMap().values();
        if (allSnapshots.isEmpty()) {
            // Fallback: return local metrics if no snapshots available yet
            return metricsEndpoint.allMetrics();
        }
        if (allSnapshots.size() == 1) {
            return stripMetadata(allSnapshots.iterator().next());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jvm", aggregateJvm(allSnapshots));
        result.put("processMetrics", aggregateProcess(allSnapshots));
        result.put("garbageCollector", aggregateGc(allSnapshots));
        result.put("http.server.requests", aggregateHttp(allSnapshots));
        result.put("cache", aggregateCache(allSnapshots));
        result.put("databases", aggregateDatabases(allSnapshots));
        result.put("services", aggregateServices(allSnapshots));
        return result;
    }

    // --- Aggregation methods ---

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Long>> aggregateJvm(Collection<Map<String, Object>> snapshots) {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        for (Map<String, Object> snapshot : snapshots) {
            var jvm = (Map<String, Map<String, Number>>) snapshot.get("jvm");
            if (jvm == null) {
                continue;
            }
            for (Map.Entry<String, Map<String, Number>> entry : jvm.entrySet()) {
                result.merge(entry.getKey(), new LinkedHashMap<>(Map.of("committed", entry.getValue().get("committed").longValue(), "max", entry.getValue().get("max").longValue(),
                        "used", entry.getValue().get("used").longValue())), (existing, newVal) -> {
                            existing.put("committed", existing.get("committed") + newVal.get("committed"));
                            existing.put("max", existing.get("max") + newVal.get("max"));
                            existing.put("used", existing.get("used") + newVal.get("used"));
                            return existing;
                        });
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Number> aggregateProcess(Collection<Map<String, Object>> snapshots) {
        Map<String, Number> result = new LinkedHashMap<>();
        int count = 0;
        for (Map<String, Object> snapshot : snapshots) {
            var pm = (Map<String, Number>) snapshot.get("processMetrics");
            if (pm == null) {
                continue;
            }
            count++;
            for (Map.Entry<String, Number> entry : pm.entrySet()) {
                result.merge(entry.getKey(), entry.getValue().doubleValue(), (a, b) -> a.doubleValue() + b.doubleValue());
            }
        }
        if (count > 1) {
            // Average CPU metrics, keep sums for others
            for (String avgKey : List.of("system.cpu.usage", "process.cpu.usage", "system.load.average.1m")) {
                if (result.containsKey(avgKey)) {
                    result.put(avgKey, result.get(avgKey).doubleValue() / count);
                }
            }
            // Min for start time, max for uptime
            double minStart = Double.MAX_VALUE;
            double maxUptime = 0;
            for (Map<String, Object> snapshot : snapshots) {
                var pm = (Map<String, Number>) snapshot.get("processMetrics");
                if (pm == null) {
                    continue;
                }
                double start = pm.getOrDefault("process.start.time", 0).doubleValue();
                double uptime = pm.getOrDefault("process.uptime", 0).doubleValue();
                if (start > 0 && start < minStart) {
                    minStart = start;
                }
                if (uptime > maxUptime) {
                    maxUptime = uptime;
                }
            }
            if (minStart < Double.MAX_VALUE) {
                result.put("process.start.time", minStart);
            }
            result.put("process.uptime", maxUptime);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> aggregateGc(Collection<Map<String, Object>> snapshots) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Number> timerAgg = null;

        for (Map<String, Object> snapshot : snapshots) {
            var gc = (Map<String, Object>) snapshot.get("garbageCollector");
            if (gc == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : gc.entrySet()) {
                if ("jvm.gc.pause".equals(entry.getKey())) {
                    timerAgg = mergeTimerSummary(timerAgg, (Map<String, Number>) entry.getValue());
                }
                else {
                    result.merge(entry.getKey(), ((Number) entry.getValue()).doubleValue(), (a, b) -> ((Number) a).doubleValue() + ((Number) b).doubleValue());
                }
            }
        }
        result.put("jvm.gc.pause", timerAgg != null ? timerAgg : emptyTimerSummary());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> aggregateHttp(Collection<Map<String, Object>> snapshots) {
        long totalCount = 0;
        Map<String, Map<String, Number>> perCode = new TreeMap<>();

        for (Map<String, Object> snapshot : snapshots) {
            var http = (Map<String, Object>) snapshot.get("http.server.requests");
            if (http == null) {
                continue;
            }
            var all = (Map<String, Number>) http.get("all");
            if (all != null) {
                totalCount += all.getOrDefault("count", 0).longValue();
            }
            var codes = (Map<String, Map<String, Number>>) http.get("percode");
            if (codes != null) {
                for (Map.Entry<String, Map<String, Number>> entry : codes.entrySet()) {
                    perCode.merge(entry.getKey(), new LinkedHashMap<>(entry.getValue()), this::mergeCountMeanMax);
                }
            }
        }

        return Map.of("all", Map.of("count", totalCount), "percode", perCode);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Number>> aggregateCache(Collection<Map<String, Object>> snapshots) {
        Map<String, Map<String, Number>> result = new TreeMap<>();
        for (Map<String, Object> snapshot : snapshots) {
            var caches = (Map<String, Map<String, Number>>) snapshot.get("cache");
            if (caches == null) {
                continue;
            }
            for (Map.Entry<String, Map<String, Number>> entry : caches.entrySet()) {
                result.merge(entry.getKey(), new LinkedHashMap<>(entry.getValue()), (existing, newVal) -> {
                    for (Map.Entry<String, Number> metric : newVal.entrySet()) {
                        existing.merge(metric.getKey(), metric.getValue(), (a, b) -> a.doubleValue() + b.doubleValue());
                    }
                    return existing;
                });
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> aggregateDatabases(Collection<Map<String, Object>> snapshots) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Double> gauges = new LinkedHashMap<>();
        Map<String, Map<String, Number>> timers = new LinkedHashMap<>();

        for (Map<String, Object> snapshot : snapshots) {
            var db = (Map<String, Object>) snapshot.get("databases");
            if (db == null) {
                continue;
            }
            for (String gaugeKey : List.of("min", "max", "idle", "active", "pending", "connections")) {
                var wrapper = (Map<String, Number>) db.get(gaugeKey);
                if (wrapper != null) {
                    gauges.merge(gaugeKey, wrapper.getOrDefault("value", 0).doubleValue(), Double::sum);
                }
            }
            for (String timerKey : List.of("acquire", "creation", "usage")) {
                var timer = (Map<String, Number>) db.get(timerKey);
                if (timer != null) {
                    timers.merge(timerKey, new LinkedHashMap<>(timer), this::mergeTimerSummary);
                }
            }
        }

        for (Map.Entry<String, Double> entry : gauges.entrySet()) {
            result.put(entry.getKey(), Map.of("value", entry.getValue()));
        }
        for (Map.Entry<String, Map<String, Number>> entry : timers.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Map<String, Number>>> aggregateServices(Collection<Map<String, Object>> snapshots) {
        Map<String, Map<String, Map<String, Number>>> result = new TreeMap<>();
        for (Map<String, Object> snapshot : snapshots) {
            var services = (Map<String, Map<String, Map<String, Number>>>) snapshot.get("services");
            if (services == null) {
                continue;
            }
            for (Map.Entry<String, Map<String, Map<String, Number>>> uriEntry : services.entrySet()) {
                result.computeIfAbsent(uriEntry.getKey(), k -> new LinkedHashMap<>());
                for (Map.Entry<String, Map<String, Number>> methodEntry : uriEntry.getValue().entrySet()) {
                    result.get(uriEntry.getKey()).merge(methodEntry.getKey(), new LinkedHashMap<>(methodEntry.getValue()), this::mergeCountMeanMax);
                }
            }
        }
        return result;
    }

    // --- Merge helpers ---

    private Map<String, Number> mergeCountMeanMax(Map<String, Number> a, Map<String, Number> b) {
        long countA = a.getOrDefault("count", 0).longValue();
        long countB = b.getOrDefault("count", 0).longValue();
        long totalCount = countA + countB;
        double weightedMean = totalCount > 0 ? (a.getOrDefault("mean", 0).doubleValue() * countA + b.getOrDefault("mean", 0).doubleValue() * countB) / totalCount : 0;
        double maxVal = Math.max(a.getOrDefault("max", 0).doubleValue(), b.getOrDefault("max", 0).doubleValue());
        Map<String, Number> result = new LinkedHashMap<>();
        result.put("count", totalCount);
        result.put("mean", weightedMean);
        result.put("max", maxVal);
        return result;
    }

    private Map<String, Number> mergeTimerSummary(Map<String, Number> a, Map<String, Number> b) {
        if (a == null) {
            return b != null ? new LinkedHashMap<>(b) : emptyTimerSummary();
        }
        if (b == null) {
            return new LinkedHashMap<>(a);
        }
        long countA = a.getOrDefault("count", 0).longValue();
        long countB = b.getOrDefault("count", 0).longValue();
        long totalCount = countA + countB;
        double weightedMean = totalCount > 0 ? (a.getOrDefault("mean", 0).doubleValue() * countA + b.getOrDefault("mean", 0).doubleValue() * countB) / totalCount : 0;

        Map<String, Number> result = new LinkedHashMap<>();
        result.put("count", totalCount);
        result.put("mean", weightedMean);
        result.put("max", Math.max(a.getOrDefault("max", 0).doubleValue(), b.getOrDefault("max", 0).doubleValue()));
        result.put("totalTime", a.getOrDefault("totalTime", 0).doubleValue() + b.getOrDefault("totalTime", 0).doubleValue());
        // Percentiles: take max as conservative estimate across nodes
        for (String p : List.of("0.0", "0.5", "0.75", "0.95", "0.99", "1.0")) {
            result.put(p, Math.max(a.getOrDefault(p, 0).doubleValue(), b.getOrDefault(p, 0).doubleValue()));
        }
        return result;
    }

    private static Map<String, Number> emptyTimerSummary() {
        Map<String, Number> empty = new LinkedHashMap<>();
        empty.put("count", 0);
        empty.put("mean", 0.0);
        empty.put("max", 0.0);
        empty.put("totalTime", 0.0);
        empty.put("0.0", 0.0);
        empty.put("0.5", 0.0);
        empty.put("0.75", 0.0);
        empty.put("0.95", 0.0);
        empty.put("0.99", 0.0);
        empty.put("1.0", 0.0);
        return empty;
    }

    private Map<String, Object> stripMetadata(Map<String, Object> snapshot) {
        Map<String, Object> result = new LinkedHashMap<>(snapshot);
        result.remove(META_NODE_ID);
        result.remove(META_NODE_LABEL);
        result.remove(META_TIMESTAMP);
        return result;
    }

    private String formatNodeLabel(Member member) {
        var address = member.getAddress();
        return address.getHost() + ":" + address.getPort();
    }

    private IMap<String, Map<String, Object>> getMap() {
        return hazelcastInstance.getMap(MAP_NAME);
    }
}
