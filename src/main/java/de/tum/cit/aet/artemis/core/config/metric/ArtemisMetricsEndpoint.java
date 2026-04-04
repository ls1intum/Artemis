package de.tum.cit.aet.artemis.core.config.metric;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;

/**
 * Actuator endpoint that exposes JVM, process, HTTP, cache, datasource and garbage-collector
 * metrics in the format expected by the Artemis admin metrics UI.
 * <p>
 * This replaces the former JHipster {@code JHipsterMetricsEndpoint} and collects data from
 * Micrometer's {@link MeterRegistry} and JMX MBeans.
 * <p>
 * Extended by {@link de.tum.cit.aet.artemis.core.web.CustomMetricsExtension} which adds
 * active-user counts.
 * <p>
 * TODO: Make metrics multi-node capable. Currently this endpoint returns metrics from
 * the local JVM only. To support multi-node deployments:
 * 1. Each node could push its metrics into a shared Hazelcast IMap (keyed by node ID)
 * 2. This endpoint would then aggregate metrics from all nodes in the map
 * 3. Alternatively, use Prometheus federation or a central metrics store (Grafana/Mimir)
 * and query aggregated metrics from there instead of collecting locally
 * 4. The client UI would need a node selector dropdown to view per-node or aggregated metrics
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
@Endpoint(id = "artemismetrics")
public class ArtemisMetricsEndpoint {

    private static final TimeUnit MS = TimeUnit.MILLISECONDS;

    private final MeterRegistry meterRegistry;

    public ArtemisMetricsEndpoint(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Returns all Artemis-specific metrics.
     *
     * @return a map keyed by category containing metric details
     */
    @ReadOperation
    public Map<String, Object> allMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("jvm", jvmMemoryMetrics());
        metrics.put("processMetrics", processMetrics());
        metrics.put("garbageCollector", garbageCollectorMetrics());
        metrics.put("http.server.requests", httpRequestMetrics());
        metrics.put("cache", cacheMetrics());
        metrics.put("databases", databaseMetrics());
        metrics.put("services", endpointMetrics());
        return metrics;
    }

    /**
     * Collects JVM memory metrics per memory pool in the format:
     * { "poolName": { "committed": bytes, "max": bytes, "used": bytes } }
     */
    private Map<String, Map<String, Long>> jvmMemoryMetrics() {
        Map<String, Map<String, Long>> jvm = new LinkedHashMap<>();

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        var heap = memoryBean.getHeapMemoryUsage();
        jvm.put("Heap", Map.of("committed", heap.getCommitted(), "max", heap.getMax(), "used", heap.getUsed()));

        var nonHeap = memoryBean.getNonHeapMemoryUsage();
        long nonHeapMax = nonHeap.getMax() == -1 ? nonHeap.getCommitted() : nonHeap.getMax();
        jvm.put("Non-Heap", Map.of("committed", nonHeap.getCommitted(), "max", nonHeapMax, "used", nonHeap.getUsed()));

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            var usage = pool.getUsage();
            if (usage != null) {
                long max = usage.getMax() == -1 ? usage.getCommitted() : usage.getMax();
                jvm.put(pool.getName(), Map.of("committed", usage.getCommitted(), "max", max, "used", usage.getUsed()));
            }
        }

        return jvm;
    }

    /**
     * Collects process-level metrics (CPU, uptime, file descriptors).
     */
    private Map<String, Number> processMetrics() {
        Map<String, Number> pm = new LinkedHashMap<>();
        pm.put("system.cpu.usage", gaugeValue("system.cpu.usage"));
        pm.put("system.cpu.count", gaugeValue("system.cpu.count"));
        pm.put("system.load.average.1m", gaugeValue("system.load.average.1m"));
        pm.put("process.cpu.usage", gaugeValue("process.cpu.usage"));
        pm.put("process.files.max", gaugeValue("process.files.max"));
        pm.put("process.files.open", gaugeValue("process.files.open"));
        pm.put("process.start.time", gaugeValue("process.start.time"));
        pm.put("process.uptime", gaugeValue("process.uptime"));
        return pm;
    }

    /**
     * Collects garbage collector and class loading metrics.
     */
    private Map<String, Object> garbageCollectorMetrics() {
        Map<String, Object> gc = new LinkedHashMap<>();
        gc.put("jvm.gc.live.data.size", gaugeValue("jvm.gc.live.data.size"));
        gc.put("jvm.gc.max.data.size", gaugeValue("jvm.gc.max.data.size"));
        gc.put("jvm.gc.memory.promoted", counterValue("jvm.gc.memory.promoted"));
        gc.put("jvm.gc.memory.allocated", counterValue("jvm.gc.memory.allocated"));
        gc.put("classesLoaded", gaugeValue("jvm.classes.loaded"));
        gc.put("classesUnloaded", counterValue("jvm.classes.unloaded"));
        gc.put("jvm.gc.pause", timerSummary("jvm.gc.pause"));
        return gc;
    }

    /**
     * Collects HTTP server request metrics grouped by status code.
     * Format: { "all": { "count": N }, "percode": { "200": { "max": ms, "mean": ms, "count": N }, ... } }
     */
    private Map<String, Object> httpRequestMetrics() {
        Map<String, Object> http = new LinkedHashMap<>();
        Map<String, Map<String, Number>> perCode = new TreeMap<>();

        long totalCount = 0;
        for (Timer timer : meterRegistry.find("http.server.requests").timers()) {
            String status = timer.getId().getTag("status");
            if (status == null) {
                status = "unknown";
            }
            long count = timer.count();
            double mean = timer.mean(MS);
            double max = timer.max(MS);
            totalCount += count;

            perCode.merge(status, newMutableMap(count, mean, max), (existing, newVal) -> {
                long existingCount = existing.get("count").longValue();
                double existingMean = existing.get("mean").doubleValue();
                long mergedCount = existingCount + newVal.get("count").longValue();
                double weightedMean = mergedCount > 0 ? (existingMean * existingCount + newVal.get("mean").doubleValue() * newVal.get("count").longValue()) / mergedCount : 0;
                existing.put("count", mergedCount);
                existing.put("mean", weightedMean);
                existing.put("max", Math.max(existing.get("max").doubleValue(), newVal.get("max").doubleValue()));
                return existing;
            });
        }

        http.put("all", Map.of("count", totalCount));
        http.put("percode", perCode);
        return http;
    }

    /**
     * Collects cache metrics (hits, misses, puts, evictions, removals) per cache name.
     */
    private Map<String, Map<String, Number>> cacheMetrics() {
        Map<String, Map<String, Number>> caches = new TreeMap<>();

        meterRegistry.find("cache.gets").meters().forEach(meter -> processCacheMeter(caches, meter));
        meterRegistry.find("cache.puts").meters().forEach(meter -> processCacheMeter(caches, meter));
        meterRegistry.find("cache.removals").meters().forEach(meter -> processCacheMeter(caches, meter));
        meterRegistry.find("cache.evictions").meters().forEach(meter -> processCacheMeter(caches, meter));

        return caches;
    }

    private void processCacheMeter(Map<String, Map<String, Number>> caches, Meter meter) {
        String cacheName = meter.getId().getTag("cache");
        if (cacheName == null) {
            return;
        }
        String metricName = meter.getId().getName();
        String result = meter.getId().getTag("result");
        String key = result != null ? metricName + "." + result : metricName;

        caches.computeIfAbsent(cacheName, k -> {
            Map<String, Number> defaults = new LinkedHashMap<>();
            defaults.put("cache.gets.hit", 0.0);
            defaults.put("cache.gets.miss", 0.0);
            defaults.put("cache.puts", 0.0);
            defaults.put("cache.removals", 0.0);
            defaults.put("cache.evictions", 0.0);
            return defaults;
        });

        double value = 0;
        for (var measurement : meter.measure()) {
            value += measurement.getValue();
        }
        caches.get(cacheName).put(key, value);
    }

    /**
     * Collects datasource / connection pool metrics.
     */
    private Map<String, Object> databaseMetrics() {
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("min", Map.of("value", gaugeValue("hikaricp.connections.min")));
        db.put("max", Map.of("value", gaugeValue("hikaricp.connections.max")));
        db.put("idle", Map.of("value", gaugeValue("hikaricp.connections.idle")));
        db.put("active", Map.of("value", gaugeValue("hikaricp.connections.active")));
        db.put("pending", Map.of("value", gaugeValue("hikaricp.connections.pending")));
        db.put("connections", Map.of("value", gaugeValue("hikaricp.connections")));
        db.put("acquire", timerSummary("hikaricp.connections.acquire"));
        db.put("creation", timerSummary("hikaricp.connections.creation"));
        db.put("usage", timerSummary("hikaricp.connections.usage"));
        return db;
    }

    /**
     * Collects per-endpoint request metrics grouped by URI and HTTP method.
     * Format: { "/api/courses": { "GET": { "count": N, "mean": ms, "max": ms } } }
     */
    private Map<String, Map<String, Map<String, Number>>> endpointMetrics() {
        Map<String, Map<String, Map<String, Number>>> services = new TreeMap<>();

        for (Timer timer : meterRegistry.find("http.server.requests").timers()) {
            String uri = timer.getId().getTag("uri");
            String method = timer.getId().getTag("method");
            if (uri == null || method == null) {
                continue;
            }

            long count = timer.count();
            double mean = timer.mean(MS);
            double max = timer.max(MS);

            services.computeIfAbsent(uri, k -> new LinkedHashMap<>());
            services.get(uri).merge(method, newMutableMap(count, mean, max), (existing, newVal) -> {
                long existingCount = existing.get("count").longValue();
                long mergedCount = existingCount + newVal.get("count").longValue();
                existing.put("count", mergedCount);
                existing.put("mean",
                        mergedCount > 0 ? (existing.get("mean").doubleValue() * existingCount + newVal.get("mean").doubleValue() * newVal.get("count").longValue()) / mergedCount
                                : 0.0);
                existing.put("max", Math.max(existing.get("max").doubleValue(), newVal.get("max").doubleValue()));
                return existing;
            });
        }

        return services;
    }

    // --- Helper methods ---

    private static Map<String, Number> newMutableMap(long count, double mean, double max) {
        Map<String, Number> map = new LinkedHashMap<>();
        map.put("count", count);
        map.put("mean", mean);
        map.put("max", max);
        return map;
    }

    private double gaugeValue(String meterName) {
        var gauge = meterRegistry.find(meterName).gauge();
        if (gauge != null) {
            return gauge.value();
        }
        var timeGauge = meterRegistry.find(meterName).timeGauge();
        if (timeGauge != null) {
            return timeGauge.value();
        }
        return 0;
    }

    private double counterValue(String meterName) {
        var counter = meterRegistry.find(meterName).counter();
        if (counter != null) {
            return counter.count();
        }
        var functionCounter = meterRegistry.find(meterName).functionCounter();
        if (functionCounter != null) {
            return functionCounter.count();
        }
        return 0;
    }

    private Map<String, Number> timerSummary(String meterName) {
        Map<String, Number> summary = new LinkedHashMap<>();
        var timer = meterRegistry.find(meterName).timer();
        if (timer != null) {
            summary.put("count", timer.count());
            summary.put("mean", timer.mean(MS));
            summary.put("max", timer.max(MS));
            summary.put("totalTime", timer.totalTime(MS));

            // Extract percentiles from the histogram snapshot (only available if publishPercentiles is configured)
            Map<Double, Double> percentileMap = new LinkedHashMap<>();
            for (ValueAtPercentile vp : timer.takeSnapshot().percentileValues()) {
                percentileMap.put(vp.percentile(), vp.value(MS));
            }
            summary.put("0.0", percentileMap.getOrDefault(0.0, 0.0));
            summary.put("0.5", percentileMap.getOrDefault(0.5, 0.0));
            summary.put("0.75", percentileMap.getOrDefault(0.75, 0.0));
            summary.put("0.95", percentileMap.getOrDefault(0.95, 0.0));
            summary.put("0.99", percentileMap.getOrDefault(0.99, 0.0));
            summary.put("1.0", percentileMap.getOrDefault(1.0, 0.0));
        }
        else {
            summary.put("count", 0);
            summary.put("mean", 0.0);
            summary.put("max", 0.0);
            summary.put("totalTime", 0.0);
            summary.put("0.0", 0.0);
            summary.put("0.5", 0.0);
            summary.put("0.75", 0.0);
            summary.put("0.95", 0.0);
            summary.put("0.99", 0.0);
            summary.put("1.0", 0.0);
        }
        return summary;
    }
}
