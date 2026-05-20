package de.tum.cit.aet.artemis.core.config.metric;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.zaxxer.hikari.HikariDataSource;

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

    private static final Logger log = LoggerFactory.getLogger(ArtemisMetricsEndpoint.class);

    private static final TimeUnit MS = TimeUnit.MILLISECONDS;

    private final MeterRegistry meterRegistry;

    private final ApplicationContext applicationContext;

    public ArtemisMetricsEndpoint(MeterRegistry meterRegistry, ApplicationContext applicationContext) {
        this.meterRegistry = meterRegistry;
        this.applicationContext = applicationContext;
    }

    // --- DTOs ---
    // Note: @JsonProperty with dots (e.g., "system.cpu.usage") is intentional.
    // Jackson treats the full string as a flat JSON key, matching the client's TypeScript interface.
    // Maps are only used where keys are dynamic at runtime (memory pool names, cache names, URIs, status codes).

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record MetricsResponse(Map<String, MemoryMetrics> jvm, ProcessMetrics processMetrics, GarbageCollectorMetrics garbageCollector,
            @JsonProperty("http.server.requests") HttpRequestMetrics httpServerRequests, Map<String, CacheStats> cache, DatabaseMetrics databases,
            Map<String, Map<String, RequestStats>> services) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record MemoryMetrics(long committed, long max, long used) implements java.io.Serializable {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProcessMetrics(@JsonProperty("system.cpu.usage") double systemCpuUsage, @JsonProperty("system.cpu.count") double systemCpuCount,
            @JsonProperty("system.load.average.1m") double systemLoadAverage, @JsonProperty("process.cpu.usage") double processCpuUsage,
            @JsonProperty("process.files.max") double processFilesMax, @JsonProperty("process.files.open") double processFilesOpen,
            @JsonProperty("process.start.time") double processStartTime, @JsonProperty("process.uptime") double processUptime) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TimerSummary(long count, double mean, double max, double totalTime, @JsonProperty("0.0") double p0, @JsonProperty("0.5") double p50,
            @JsonProperty("0.75") double p75, @JsonProperty("0.95") double p95, @JsonProperty("0.99") double p99, @JsonProperty("1.0") double p100) {

        static final TimerSummary EMPTY = new TimerSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record GarbageCollectorMetrics(@JsonProperty("jvm.gc.live.data.size") double liveDataSize, @JsonProperty("jvm.gc.max.data.size") double maxDataSize,
            @JsonProperty("jvm.gc.memory.promoted") double memoryPromoted, @JsonProperty("jvm.gc.memory.allocated") double memoryAllocated, double classesLoaded,
            double classesUnloaded, @JsonProperty("jvm.gc.pause") TimerSummary gcPause) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RequestCount(long count) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RequestStats(long count, double mean, double max) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record HttpRequestMetrics(RequestCount all, Map<String, RequestStats> percode) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CacheStats(@JsonProperty("cache.gets.hit") double hits, @JsonProperty("cache.gets.miss") double misses, @JsonProperty("cache.puts") double puts,
            @JsonProperty("cache.evictions") double evictions, @JsonProperty("cache.removals") double removals, @JsonProperty("cache.size") double size) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record GaugeValue(double value) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record DatabaseMetrics(GaugeValue min, GaugeValue max, GaugeValue idle, GaugeValue active, GaugeValue pending, GaugeValue connections, TimerSummary acquire,
            TimerSummary creation, TimerSummary usage) {
    }

    // --- Endpoint ---

    @ReadOperation
    public MetricsResponse allMetrics() {
        return new MetricsResponse(jvmMemoryMetrics(), processMetrics(), garbageCollectorMetrics(), httpRequestMetrics(), cacheMetrics(), databaseMetrics(), endpointMetrics());
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

            perCode.merge(status, new RequestStats(count, mean, max), ArtemisMetricsEndpoint::mergeRequestStats);
        }

        return new HttpRequestMetrics(new RequestCount(totalCount), perCode);
    }

    /**
     * Collects metrics for all Hazelcast IMaps used as distributed state.
     * <p>
     * This includes Spring {@code @Cacheable} caches backed by
     * {@link com.hazelcast.spring.cache.HazelcastCacheManager} as well as application-level
     * IMaps (rate-limit buckets, atlas session state, course notification cache, etc.).
     * Hibernate L2 cache is disabled cluster-wide, so there are no JCache / ICache regions
     * to read — see {@code documentation/docs/developer/guidelines/caching.mdx}.
     * <p>
     * Statistics are read from {@code IMap.getLocalMapStats()} (local-only); the entry
     * count uses {@code stats.getOwnedEntryCount()} rather than {@code map.size()} to
     * avoid the cluster-wide network round-trip implied by the latter.
     */
    private Map<String, CacheStats> cacheMetrics() {
        Map<String, CacheStats> result = new TreeMap<>();
        try {
            var hazelcast = applicationContext.getBean(HazelcastInstance.class);
            for (var distributedObject : hazelcast.getDistributedObjects()) {
                String name = distributedObject.getName();
                if (name.startsWith("__") || "default".equals(name) || "nodeMetrics".equals(name)) {
                    continue;
                }
                try {
                    if (distributedObject instanceof IMap<?, ?> map) {
                        var stats = map.getLocalMapStats();
                        long misses = Math.max(0, stats.getGetOperationCount() - stats.getHits());
                        result.put(name, new CacheStats(stats.getHits(), misses, stats.getPutOperationCount(), stats.getEvictionCount(), stats.getRemoveOperationCount(),
                                stats.getOwnedEntryCount()));
                    }
                }
                catch (Exception e) {
                    log.debug("Could not collect IMap metrics for '{}': {}", name, e.getMessage());
                }
            }
        }
        catch (Exception e) {
            log.debug("Could not access HazelcastInstance for cache metrics: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Collects datasource metrics directly from HikariCP pool MXBean.
     */
    private DatabaseMetrics databaseMetrics() {
        try {
            var ds = applicationContext.getBean(DataSource.class);
            if (ds instanceof HikariDataSource hikari) {
                var pool = hikari.getHikariPoolMXBean();
                if (pool != null) {
                    return new DatabaseMetrics(new GaugeValue(hikari.getMinimumIdle()), new GaugeValue(hikari.getMaximumPoolSize()), new GaugeValue(pool.getIdleConnections()),
                            new GaugeValue(pool.getActiveConnections()), new GaugeValue(pool.getThreadsAwaitingConnection()), new GaugeValue(pool.getTotalConnections()),
                            timerSummary("hikaricp.connections.acquire"), timerSummary("hikaricp.connections.creation"), timerSummary("hikaricp.connections.usage"));
                }
            }
        }
        catch (Exception e) {
            log.debug("Could not collect datasource metrics: {}", e.getMessage());
        }
        return new DatabaseMetrics(new GaugeValue(0), new GaugeValue(0), new GaugeValue(0), new GaugeValue(0), new GaugeValue(0), new GaugeValue(0), TimerSummary.EMPTY,
                TimerSummary.EMPTY, TimerSummary.EMPTY);
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
            services.get(uri).merge(method, stats, ArtemisMetricsEndpoint::mergeRequestStats);
        }

        return services;
    }

    // --- Helpers ---

    private static RequestStats mergeRequestStats(RequestStats existing, RequestStats incoming) {
        long mergedCount = existing.count() + incoming.count();
        double weightedMean = mergedCount > 0 ? (existing.mean() * existing.count() + incoming.mean() * incoming.count()) / mergedCount : 0;
        return new RequestStats(mergedCount, weightedMean, Math.max(existing.max(), incoming.max()));
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
