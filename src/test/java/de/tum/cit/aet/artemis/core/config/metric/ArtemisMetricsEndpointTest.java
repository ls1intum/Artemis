package de.tum.cit.aet.artemis.core.config.metric;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Tests for {@link ArtemisMetricsEndpoint} to verify the metrics response format
 * matches what the admin metrics UI expects.
 */
class ArtemisMetricsEndpointTest {

    private ArtemisMetricsEndpoint endpoint;

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        endpoint = new ArtemisMetricsEndpoint(meterRegistry);
    }

    @Test
    void allMetrics_shouldReturnAllExpectedTopLevelKeys() {
        var metrics = endpoint.allMetrics();

        assertThat(metrics).containsKeys("jvm", "processMetrics", "garbageCollector", "http.server.requests", "cache", "databases", "services");
    }

    @Test
    @SuppressWarnings("unchecked")
    void jvmMetrics_shouldContainHeapAndNonHeapWithCorrectStructure() {
        var metrics = endpoint.allMetrics();
        var jvm = (Map<String, Map<String, Long>>) metrics.get("jvm");

        assertThat(jvm).containsKeys("Heap", "Non-Heap");

        // Each memory pool must have committed, max, used
        var heap = jvm.get("Heap");
        assertThat(heap).containsKeys("committed", "max", "used");
        assertThat(heap.get("used")).isPositive();
        assertThat(heap.get("max")).isPositive();
        assertThat(heap.get("committed")).isPositive();

        var nonHeap = jvm.get("Non-Heap");
        assertThat(nonHeap).containsKeys("committed", "max", "used");
        assertThat(nonHeap.get("used")).isPositive();
    }

    @Test
    @SuppressWarnings("unchecked")
    void processMetrics_shouldContainExpectedKeys() {
        var metrics = endpoint.allMetrics();
        var process = (Map<String, Number>) metrics.get("processMetrics");

        assertThat(process).containsKeys("system.cpu.usage", "system.cpu.count", "process.cpu.usage", "process.start.time", "process.uptime");
    }

    @Test
    @SuppressWarnings("unchecked")
    void garbageCollectorMetrics_shouldContainExpectedKeys() {
        var metrics = endpoint.allMetrics();
        var gc = (Map<String, Object>) metrics.get("garbageCollector");

        assertThat(gc).containsKeys("classesLoaded", "classesUnloaded", "jvm.gc.pause");

        // GC pause must be a timer summary with percentile fields
        var gcPause = (Map<String, Number>) gc.get("jvm.gc.pause");
        assertThat(gcPause).containsKeys("count", "mean", "max", "0.0", "0.5", "0.75", "0.95", "0.99");
    }

    @Test
    @SuppressWarnings("unchecked")
    void httpRequestMetrics_shouldContainAllAndPercode() {
        // Register a timer to simulate HTTP requests
        meterRegistry.timer("http.server.requests", "status", "200", "method", "GET", "uri", "/api/test").record(java.time.Duration.ofMillis(50));

        var metrics = endpoint.allMetrics();
        var http = (Map<String, Object>) metrics.get("http.server.requests");

        assertThat(http).containsKeys("all", "percode");

        var all = (Map<String, Number>) http.get("all");
        assertThat(all.get("count")).isEqualTo(1L);

        var percode = (Map<String, Map<String, Number>>) http.get("percode");
        assertThat(percode).containsKey("200");
        assertThat(percode.get("200")).containsKeys("count", "mean", "max");
        assertThat(percode.get("200").get("count").longValue()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cacheMetrics_shouldContainExpectedStructurePerCache() {
        // Register cache meters to simulate cache activity
        meterRegistry.counter("cache.gets", "cache", "testCache", "result", "hit").increment(10);
        meterRegistry.counter("cache.gets", "cache", "testCache", "result", "miss").increment(2);

        var metrics = endpoint.allMetrics();
        var caches = (Map<String, Map<String, Number>>) metrics.get("cache");

        assertThat(caches).containsKey("testCache");
        var testCache = caches.get("testCache");
        assertThat(testCache).containsKeys("cache.gets.hit", "cache.gets.miss", "cache.puts", "cache.removals", "cache.evictions");
    }

    @Test
    @SuppressWarnings("unchecked")
    void databaseMetrics_shouldContainConnectionPoolKeys() {
        var metrics = endpoint.allMetrics();
        var db = (Map<String, Object>) metrics.get("databases");

        assertThat(db).containsKeys("min", "max", "idle", "active", "pending", "connections", "acquire", "creation", "usage");

        // Value wrappers must have "value" key
        var min = (Map<String, Number>) db.get("min");
        assertThat(min).containsKey("value");

        // Timer summaries must have percentile keys
        var acquire = (Map<String, Number>) db.get("acquire");
        assertThat(acquire).containsKeys("count", "mean", "max", "0.0", "0.5", "0.75", "0.95", "0.99");
    }

    @Test
    @SuppressWarnings("unchecked")
    void endpointMetrics_shouldGroupByUriAndMethod() {
        // Register timers simulating endpoint requests
        meterRegistry.timer("http.server.requests", "uri", "/api/courses", "method", "GET", "status", "200").record(java.time.Duration.ofMillis(100));
        meterRegistry.timer("http.server.requests", "uri", "/api/courses", "method", "POST", "status", "201").record(java.time.Duration.ofMillis(200));

        var metrics = endpoint.allMetrics();
        var services = (Map<String, Map<String, Map<String, Number>>>) metrics.get("services");

        assertThat(services).containsKey("/api/courses");
        assertThat(services.get("/api/courses")).containsKeys("GET", "POST");
        assertThat(services.get("/api/courses").get("GET")).containsKeys("count", "mean", "max");
    }
}
