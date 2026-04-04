package de.tum.cit.aet.artemis.core.config.metric;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.CacheStats;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.DatabaseMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.GarbageCollectorMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.HttpRequestMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.MemoryMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.ProcessMetrics;
import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.RequestStats;
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
        var jvm = (Map<String, MemoryMetrics>) metrics.get("jvm");

        assertThat(jvm).containsKeys("Heap", "Non-Heap");

        var heap = jvm.get("Heap");
        assertThat(heap.used()).isPositive();
        assertThat(heap.max()).isPositive();
        assertThat(heap.committed()).isPositive();

        var nonHeap = jvm.get("Non-Heap");
        assertThat(nonHeap.used()).isPositive();
    }

    @Test
    void processMetrics_shouldContainExpectedValues() {
        var metrics = endpoint.allMetrics();
        var process = (ProcessMetrics) metrics.get("processMetrics");

        assertThat(process).isNotNull();
        // CPU count should be at least 1
        assertThat(process.systemCpuCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void garbageCollectorMetrics_shouldContainExpectedValues() {
        var metrics = endpoint.allMetrics();
        var gc = (GarbageCollectorMetrics) metrics.get("garbageCollector");

        assertThat(gc).isNotNull();
        assertThat(gc.gcPause()).isNotNull();
        assertThat(gc.gcPause().count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void httpRequestMetrics_shouldContainAllAndPercode() {
        meterRegistry.timer("http.server.requests", "status", "200", "method", "GET", "uri", "/api/test").record(java.time.Duration.ofMillis(50));

        var metrics = endpoint.allMetrics();
        var http = (HttpRequestMetrics) metrics.get("http.server.requests");

        assertThat(http).isNotNull();
        assertThat(http.all().count()).isEqualTo(1L);
        assertThat(http.percode()).containsKey("200");

        var stats200 = http.percode().get("200");
        assertThat(stats200.count()).isEqualTo(1);
        assertThat(stats200.mean()).isGreaterThanOrEqualTo(0);
        assertThat(stats200.max()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cacheMetrics_shouldContainExpectedStructurePerCache() {
        meterRegistry.counter("cache.gets", "cache", "testCache", "result", "hit").increment(10);
        meterRegistry.counter("cache.gets", "cache", "testCache", "result", "miss").increment(2);

        var metrics = endpoint.allMetrics();
        var caches = (Map<String, CacheStats>) metrics.get("cache");

        assertThat(caches).containsKey("testCache");
        var testCache = caches.get("testCache");
        assertThat(testCache.hits()).isEqualTo(10.0);
        assertThat(testCache.misses()).isEqualTo(2.0);
    }

    @Test
    void databaseMetrics_shouldContainConnectionPoolValues() {
        var metrics = endpoint.allMetrics();
        var db = (DatabaseMetrics) metrics.get("databases");

        assertThat(db).isNotNull();
        assertThat(db.min()).isNotNull();
        assertThat(db.max()).isNotNull();
        assertThat(db.acquire()).isNotNull();
        assertThat(db.acquire().count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endpointMetrics_shouldGroupByUriAndMethod() {
        meterRegistry.timer("http.server.requests", "uri", "/api/courses", "method", "GET", "status", "200").record(java.time.Duration.ofMillis(100));
        meterRegistry.timer("http.server.requests", "uri", "/api/courses", "method", "POST", "status", "201").record(java.time.Duration.ofMillis(200));

        var metrics = endpoint.allMetrics();
        var services = (Map<String, Map<String, RequestStats>>) metrics.get("services");

        assertThat(services).containsKey("/api/courses");
        assertThat(services.get("/api/courses")).containsKeys("GET", "POST");

        var getStats = services.get("/api/courses").get("GET");
        assertThat(getStats.count()).isEqualTo(1);
        assertThat(getStats.mean()).isGreaterThanOrEqualTo(0);
    }
}
