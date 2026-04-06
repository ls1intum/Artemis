package de.tum.cit.aet.artemis.core.config.metric;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration test that verifies the metrics endpoint returns real cache and datasource
 * data when running with a full Spring context (Hazelcast, HikariCP, Hibernate).
 */
class ArtemisMetricsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ArtemisMetricsEndpoint metricsEndpoint;

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
    void metricsEndpoint_shouldReturnDatabaseMetricsWithPositiveValues() {
        // Trigger DB activity to ensure HikariCP pool is initialized
        userTestRepository.count();

        var response = metricsEndpoint.allMetrics();
        assertThat(response.databases()).isNotNull();
        assertThat(response.databases().max().value()).as("DB max connections should be positive").isPositive();
        assertThat(response.databases().connections().value()).as("DB total connections should be positive").isPositive();
    }

    @Test
    void metricsEndpoint_shouldReturnCacheMetrics() {
        // Trigger some cache activity
        userTestRepository.count();

        var cache = metricsEndpoint.allMetrics().cache();
        assertThat(cache).as("Cache metrics should not be empty").isNotEmpty();
        // websocketBrokerStatus is always present in the test Hazelcast instance
        assertThat(cache).containsKey("websocketBrokerStatus");
    }
}
