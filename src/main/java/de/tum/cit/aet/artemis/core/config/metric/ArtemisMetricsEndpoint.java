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

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;

/**
 * Actuator endpoint that exposes JVM, process, HTTP, cache, datasource and garbage-collector
 * metrics in the format expected by the Artemis admin metrics UI.
 * <p>
 * Collects data from Micrometer's {@link MeterRegistry} and JMX MBeans.
 * Extended by {@link de.tum.cit.aet.artemis.core.web.CustomMetricsExtension} which adds
 * active-user counts and multi-node aggregation via {@link NodeMetricsCollector}.
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

    // --- Public DTOs (serialized as JSON for the client) ---

    public record MemoryMetrics(long committed, long max, long used) {
    }

    public record ProcessMetrics(@JsonProperty("system.cpu.usage") double systemCpuUsage, @JsonProperty("system.cpu.count") double systemCpuCount,
            @JsonProperty("system.load.average.1m") double systemLoadAverage, @JsonProperty("process.cpu.usage") double processCpuUsage,
            @JsonProperty("process.files.max") double processFilesMax, @JsonProperty("process.files.open") double processFilesOpen,
            @JsonProperty("process.start.time") double processStartTime, @JsonProperty("process.uptime") double processUptime) {
    }

    public record TimerSummary(long count, double mean, double max, double totalTime, @JsonProperty("0.0") double p0, @JsonProperty("0.5") double p50,
            @JsonProperty("0.75") double p75, @JsonProperty("0.95") double p95, @JsonProperty("0.99") double p99, @JsonProperty("1.0") double p100) {

        static final TimerSummary EMPTY = new TimerSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public record GarbageCollectorMetrics(@JsonProperty("jvm.gc.live.data.size") double liveDataSize, @JsonProperty("jvm.gc.max.data.size") double maxDataSize,
            @JsonProperty("jvm.gc.memory.promoted") double memoryPromoted, @JsonProperty("jvm.gc.memory.allocated") double memoryAllocated, double classesLoaded,
            double classesUnloaded, @JsonProperty("jvm.gc.pause") TimerSummary gcPause) {
    }

    public record RequestCount(long count) {
    }

    public record RequestStats(long count, double mean, double max) {
    }

    public record HttpRequestMetrics(RequestCount all, Map<String, RequestStats> percode) {
    }

    public record CacheStats(@JsonProperty("cache.gets.hit") double hits, @JsonProperty("cache.gets.miss") double misses, @JsonProperty("cache.puts") double puts,
            @JsonProperty("cache.evictions") double evictions, @JsonProperty("cache.removals") double removals, @JsonProperty("cache.size") double size) {
    }

    public record GaugeValue(double value) {
    }

    public record DatabaseMetrics(GaugeValue min, GaugeValue max, GaugeValue idle, GaugeValue active, GaugeValue pending, GaugeValue connections, TimerSummary acquire,
            TimerSummary creation, TimerSummary usage) {
    }

    // --- Endpoint ---

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

    // --- Collection methods ---

    private Map<String, MemoryMetrics> jvmMemoryMetrics() {
        Map<String, MemoryMetrics> jvm = new LinkedHashMap<>();

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        var heap = memoryBean.getHeapMemoryUsage();
        jvm.put("Heap", new MemoryMetrics(heap.getCommitted(), heap.getMax(), heap.getUsed()));

        var nonHeap = memoryBean.getNonHeapMemoryUsage();
        long nonHeapMax = nonHeap.getMax() == -1 ? nonHeap.getCommitted() : nonHeap.getMax();
        jvm.put("Non-Heap", new MemoryMetrics(nonHeap.getCommitted(), nonHeapMax, nonHeap.getUsed()));

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            var usage = pool.getUsage();
            if (usage != null) {
                long max = usage.getMax() == -1 ? usage.getCommitted() : usage.getMax();
                jvm.put(pool.getName(), new MemoryMetrics(usage.getCommitted(), max, usage.getUsed()));
            }
        }

        return jvm;
    }

    private ProcessMetrics processMetrics() {
        return new ProcessMetrics(gaugeValue("system.cpu.usage"), gaugeValue("system.cpu.count"), gaugeValue("system.load.average.1m"), gaugeValue("process.cpu.usage"),
                gaugeValue("process.files.max"), gaugeValue("process.files.open"), gaugeValue("process.start.time"), gaugeValue("process.uptime"));
    }

    private GarbageCollectorMetrics garbageCollectorMetrics() {
        return new GarbageCollectorMetrics(gaugeValue("jvm.gc.live.data.size"), gaugeValue("jvm.gc.max.data.size"), counterValue("jvm.gc.memory.promoted"),
                counterValue("jvm.gc.memory.allocated"), gaugeValue("jvm.classes.loaded"), counterValue("jvm.classes.unloaded"), timerSummary("jvm.gc.pause"));
    }

    private HttpRequestMetrics httpRequestMetrics() {
        Map<String, RequestStats> perCode = new TreeMap<>();
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

            perCode.merge(status, new RequestStats(count, mean, max), (existing, incoming) -> {
                long mergedCount = existing.count() + incoming.count();
                double weightedMean = mergedCount > 0 ? (existing.mean() * existing.count() + incoming.mean() * incoming.count()) / mergedCount : 0;
                return new RequestStats(mergedCount, weightedMean, Math.max(existing.max(), incoming.max()));
            });
        }

        return new HttpRequestMetrics(new RequestCount(totalCount), perCode);
    }

    private Map<String, CacheStats> cacheMetrics() {
        // Collect raw values per cache name
        Map<String, Map<String, Double>> raw = new TreeMap<>();
        for (String meterName : new String[] { "cache.gets", "cache.puts", "cache.evictions", "cache.size" }) {
            meterRegistry.find(meterName).meters().forEach(meter -> collectCacheMeter(raw, meter));
        }
        // Convert to CacheStats records
        Map<String, CacheStats> result = new TreeMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : raw.entrySet()) {
            Map<String, Double> v = entry.getValue();
            result.put(entry.getKey(), new CacheStats(v.getOrDefault("cache.gets.hit", 0.0), v.getOrDefault("cache.gets.miss", 0.0), v.getOrDefault("cache.puts", 0.0),
                    v.getOrDefault("cache.evictions", 0.0), v.getOrDefault("cache.removals", 0.0), v.getOrDefault("cache.size", 0.0)));
        }
        return result;
    }

    private void collectCacheMeter(Map<String, Map<String, Double>> caches, Meter meter) {
        String cacheName = meter.getId().getTag("cache");
        if (cacheName == null) {
            return;
        }
        String metricName = meter.getId().getName();
        String resultTag = meter.getId().getTag("result");
        String key = resultTag != null ? metricName + "." + resultTag : metricName;

        caches.computeIfAbsent(cacheName, k -> new LinkedHashMap<>());
        double value = 0;
        for (var measurement : meter.measure()) {
            value += measurement.getValue();
        }
        caches.get(cacheName).put(key, value);
    }

    private DatabaseMetrics databaseMetrics() {
        return new DatabaseMetrics(new GaugeValue(gaugeValue("hikaricp.connections.min")), new GaugeValue(gaugeValue("hikaricp.connections.max")),
                new GaugeValue(gaugeValue("hikaricp.connections.idle")), new GaugeValue(gaugeValue("hikaricp.connections.active")),
                new GaugeValue(gaugeValue("hikaricp.connections.pending")), new GaugeValue(gaugeValue("hikaricp.connections")), timerSummary("hikaricp.connections.acquire"),
                timerSummary("hikaricp.connections.creation"), timerSummary("hikaricp.connections.usage"));
    }

    private Map<String, Map<String, RequestStats>> endpointMetrics() {
        Map<String, Map<String, RequestStats>> services = new TreeMap<>();

        for (Timer timer : meterRegistry.find("http.server.requests").timers()) {
            String uri = timer.getId().getTag("uri");
            String method = timer.getId().getTag("method");
            if (uri == null || method == null) {
                continue;
            }

            RequestStats stats = new RequestStats(timer.count(), timer.mean(MS), timer.max(MS));
            services.computeIfAbsent(uri, k -> new LinkedHashMap<>());
            services.get(uri).merge(method, stats, (existing, incoming) -> {
                long mergedCount = existing.count() + incoming.count();
                double weightedMean = mergedCount > 0 ? (existing.mean() * existing.count() + incoming.mean() * incoming.count()) / mergedCount : 0;
                return new RequestStats(mergedCount, weightedMean, Math.max(existing.max(), incoming.max()));
            });
        }

        return services;
    }

    // --- Helper methods ---

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

    private TimerSummary timerSummary(String meterName) {
        var timer = meterRegistry.find(meterName).timer();
        if (timer == null) {
            return TimerSummary.EMPTY;
        }
        Map<Double, Double> percentiles = new LinkedHashMap<>();
        for (ValueAtPercentile vp : timer.takeSnapshot().percentileValues()) {
            percentiles.put(vp.percentile(), vp.value(MS));
        }
        return new TimerSummary(timer.count(), timer.mean(MS), timer.max(MS), timer.totalTime(MS), percentiles.getOrDefault(0.0, 0.0), percentiles.getOrDefault(0.5, 0.0),
                percentiles.getOrDefault(0.75, 0.0), percentiles.getOrDefault(0.95, 0.0), percentiles.getOrDefault(0.99, 0.0), percentiles.getOrDefault(1.0, 0.0));
    }
}
