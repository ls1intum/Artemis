package de.tum.cit.aet.artemis.core.config.metric;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Integration test that verifies the metrics endpoint returns real cache and datasource
 * data when running with a full Spring context (Hazelcast, HikariCP, Hibernate).
 */
class ArtemisMetricsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ArtemisMetricsEndpoint metricsEndpoint;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void metricsEndpoint_shouldReturnJvmMetrics() {
        var response = metricsEndpoint.allMetrics();
        assertThat(response.jvm()).isNotEmpty();
        assertThat(response.jvm()).containsKey("Heap");
        assertThat(response.jvm().get("Heap").used()).isPositive();
    }

    @Test
    void metricsEndpoint_shouldReturnProcessMetrics() {
        var response = metricsEndpoint.allMetrics();
        assertThat(response.processMetrics()).isNotNull();
        assertThat(response.processMetrics().systemCpuCount()).isPositive();
        assertThat(response.processMetrics().processUptime()).isPositive();
    }

    @Test
    void meterRegistry_shouldContainHikariCpMeters() {
        // Trigger a DB query so HikariCP initializes connections
        userTestRepository.count();

        // Debug: print all hikaricp meters
        var hikariMeters = meterRegistry.getMeters().stream().filter(m -> m.getId().getName().startsWith("hikaricp")).map(Meter::getId).toList();

        assertThat(hikariMeters).as("HikariCP meters should be registered after a DB query. Found meters: " + hikariMeters).isNotEmpty();
    }

    @Test
    void meterRegistry_shouldContainCacheMeters() {
        userTestRepository.count();

        var cacheMeters = meterRegistry.getMeters().stream().filter(m -> m.getId().getName().startsWith("cache.")).toList();
        assertThat(cacheMeters).as("Cache meters should be registered").isNotEmpty();
    }

    @Test
    void metricsEndpoint_shouldReturnDatabaseMetricsWithPositiveValues() {
        // Trigger DB activity
        userTestRepository.count();

        var response = metricsEndpoint.allMetrics();
        assertThat(response.databases()).isNotNull();
        assertThat(response.databases().max().value()).as("DB max connections").isPositive();
        assertThat(response.databases().idle().value()).as("DB idle connections").isGreaterThanOrEqualTo(0);
    }

    @Test
    void metricsEndpoint_shouldReturnCacheMetrics() {
        userTestRepository.count();

        var cache = metricsEndpoint.allMetrics().cache();
        assertThat(cache).as("Cache metrics should not be empty").isNotEmpty();
        // At least one cache should have activity
        assertThat(cache.values().stream().anyMatch(s -> s.hits() > 0 || s.puts() > 0 || s.size() > 0)).as("At least one cache should have activity").isTrue();
    }
}
