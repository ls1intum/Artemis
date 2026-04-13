package de.tum.cit.aet.artemis.core.config.metric;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint.MetricsResponse;
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
        // Use a mock ApplicationContext that has no beans — cache/DB will return empty/zeros
        var emptyContext = new org.springframework.context.support.GenericApplicationContext();
        endpoint = new ArtemisMetricsEndpoint(meterRegistry, emptyContext);
    }

    @Test
    void allMetrics_shouldReturnCompleteResponse() {
        MetricsResponse response = endpoint.allMetrics();

        assertThat(response).isNotNull();
        assertThat(response.jvm()).isNotEmpty();
        assertThat(response.processMetrics()).isNotNull();
        assertThat(response.garbageCollector()).isNotNull();
        assertThat(response.httpServerRequests()).isNotNull();
        assertThat(response.cache()).isNotNull();
        assertThat(response.databases()).isNotNull();
        assertThat(response.services()).isNotNull();
    }

    @Test
    void jvmMetrics_shouldContainHeapAndNonHeapWithPositiveValues() {
        var response = endpoint.allMetrics();
        var jvm = response.jvm();

        assertThat(jvm).containsKeys("Heap", "Non-Heap");

        var heap = jvm.get("Heap");
        assertThat(heap.used()).isPositive();
        assertThat(heap.max()).isPositive();
        assertThat(heap.committed()).isPositive();

        var nonHeap = jvm.get("Non-Heap");
        assertThat(nonHeap.used()).isPositive();
    }

    @Test
    void processMetrics_shouldContainCpuAndUptimeValues() {
        var process = endpoint.allMetrics().processMetrics();

        assertThat(process).isNotNull();
        assertThat(process.systemCpuCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void garbageCollectorMetrics_shouldContainGcPause() {
        var gc = endpoint.allMetrics().garbageCollector();

        assertThat(gc).isNotNull();
        assertThat(gc.gcPause()).isNotNull();
        assertThat(gc.gcPause().count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void httpRequestMetrics_shouldAggregateByStatusCode() {
        meterRegistry.timer("http.server.requests", "status", "200", "method", "GET", "uri", "/api/test").record(java.time.Duration.ofMillis(50));

        var http = endpoint.allMetrics().httpServerRequests();

        assertThat(http).isNotNull();
        assertThat(http.all().count()).isEqualTo(1L);
        assertThat(http.percode()).containsKey("200");

        var stats200 = http.percode().get("200");
        assertThat(stats200.count()).isEqualTo(1);
        assertThat(stats200.mean()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void cacheMetrics_shouldReturnEmptyWithoutHazelcast() {
        // Without HazelcastInstance, cache metrics are empty
        var caches = endpoint.allMetrics().cache();
        assertThat(caches).isEmpty();
    }

    @Test
    void databaseMetrics_shouldContainConnectionPoolRecords() {
        var db = endpoint.allMetrics().databases();

        assertThat(db).isNotNull();
        assertThat(db.min()).isNotNull();
        assertThat(db.max()).isNotNull();
        assertThat(db.acquire()).isNotNull();
        assertThat(db.acquire().count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void endpointMetrics_shouldGroupByUriAndMethod() {
        meterRegistry.timer("http.server.requests", "uri", "/api/courses", "method", "GET", "status", "200").record(java.time.Duration.ofMillis(100));
        meterRegistry.timer("http.server.requests", "uri", "/api/courses", "method", "POST", "status", "201").record(java.time.Duration.ofMillis(200));

        var services = endpoint.allMetrics().services();

        assertThat(services).containsKey("/api/courses");
        assertThat(services.get("/api/courses")).containsKeys("GET", "POST");

        var getStats = services.get("/api/courses").get("GET");
        assertThat(getStats.count()).isEqualTo(1);
    }
}
