package de.tum.cit.aet.artemis.core.config.metric;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
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
    void meterRegistry_shouldContainHikariCpConnectionGauges() {
        // DataSourcePoolMetricsAutoConfiguration must not be excluded in application-core.yml
        // for these gauges to be registered
        var activeGauge = meterRegistry.find("hikaricp.connections.active").gauge();
        var idleGauge = meterRegistry.find("hikaricp.connections.idle").gauge();
        var maxGauge = meterRegistry.find("hikaricp.connections.max").gauge();

        assertThat(activeGauge).as("hikaricp.connections.active gauge should be registered").isNotNull();
        assertThat(idleGauge).as("hikaricp.connections.idle gauge should be registered").isNotNull();
        assertThat(maxGauge).as("hikaricp.connections.max gauge should be registered").isNotNull();
        // max connections should be > 0 if the pool is configured
        assertThat(maxGauge.value()).as("HikariCP max connections should be positive").isPositive();
    }

    @Test
    void metricsEndpoint_shouldReturnDatabaseMetricsWithPositiveMaxConnections() {
        var response = metricsEndpoint.allMetrics();
        assertThat(response.databases()).isNotNull();
        assertThat(response.databases().max()).isNotNull();
        assertThat(response.databases().max().value()).as("Database max connections should be positive").isPositive();
    }
}
