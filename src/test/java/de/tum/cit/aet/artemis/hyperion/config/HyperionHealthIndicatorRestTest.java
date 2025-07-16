package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.hyperion.AbstractHyperionRestTest;

/**
 * Integration tests for HyperionHealthIndicator.
 *
 * Tests health check functionality including service availability detection,
 * configuration reporting, error handling, and performance characteristics
 * for production monitoring systems.
 */
@Import(HyperionRestTestConfiguration.class)
@TestPropertySource(properties = { "artemis.hyperion.url=http://localhost:8080", "artemis.hyperion.api-key=test-api-key", "artemis.hyperion.connection-timeout=PT10S",
        "artemis.hyperion.read-timeout=PT30S" })
class HyperionHealthIndicatorRestTest extends AbstractHyperionRestTest {

    @Autowired
    private HyperionHealthIndicator hyperionHealthIndicator;

    /**
     * Tests successful health check scenario with complete configuration reporting.
     *
     * Validates that when Hyperion service is available, health status is UP
     * and all configuration details are properly included for operational visibility
     * while ensuring no sensitive information is exposed.
     */
    @Test
    void testHealthCheckSuccess() {
        // Given: Hyperion service responds successfully to health check
        hyperionRequestMockProvider.mockHealthCheckSuccess();

        // When: Health check is performed
        Health health = hyperionHealthIndicator.health();

        // Then: Verify UP status and configuration reporting
        assertThat(health.getStatus()).isEqualTo(Status.UP);

        Map<String, Object> details = health.getDetails();

        // Verify configuration is included for ops teams
        assertThat(details).as("Health details must include configuration for operational monitoring").containsEntry("url", "http://localhost:8080")
                .containsEntry("security", "API Key configured").containsEntry("status", "UP").containsEntry("connection_timeout", "PT10S").containsEntry("read_timeout", "PT30S");

        // Verify no sensitive data is exposed in health endpoint
        assertThat(details).as("Health check must not expose sensitive authentication data").doesNotContainKeys("api-key", "apiKey", "password", "secret", "token");

        // Verify service response metadata is included
        assertThat(details).as("Service response metadata should be available for monitoring").containsKey("version").containsKey("timestamp");

        // Verify health status indicates operational readiness
        assertThat(health.getStatus()).as("Health status must accurately reflect service availability").isEqualTo(Status.UP);

        hyperionRequestMockProvider.verify();
    }

    /**
     * Tests failure scenario with comprehensive error reporting for troubleshooting.
     *
     * Validates that when Hyperion service is unavailable, health status is DOWN
     * and error details provide actionable information for operations teams
     * while still including configuration context.
     */
    @Test
    void testHealthCheckFailure() {
        // Given: Hyperion service is unavailable (HTTP 503)
        hyperionRequestMockProvider.mockHealthCheckFailure();

        // When: Health check encounters service failure
        Health health = hyperionHealthIndicator.health();

        // Then: Verify DOWN status with actionable error information
        assertThat(health.getStatus()).as("Health status must accurately reflect service unavailability").isEqualTo(Status.DOWN);

        Map<String, Object> details = health.getDetails();

        // Verify error information is included for troubleshooting
        assertThat(details).as("Failure details must provide actionable troubleshooting information").containsEntry("status", "DOWN").containsKey("error").containsKey("url"); // Configuration
                                                                                                                                                                               // still
                                                                                                                                                                               // available
                                                                                                                                                                               // for
                                                                                                                                                                               // debugging

        // Verify error message provides specific failure context
        String errorInfo = details.get("error").toString();
        assertThat(errorInfo).as("Error message must contain specific HTTP status for troubleshooting").containsAnyOf("503", "Service Unavailable", "Connection").isNotEmpty();

        // Verify configuration is still reported for troubleshooting context
        assertThat(details.get("url")).as("URL configuration should remain available during failures for debugging").isEqualTo("http://localhost:8080");

        hyperionRequestMockProvider.verify();
    }

    /**
     * Tests timeout handling and performance characteristics of health checks.
     *
     * Validates that connection timeouts are handled gracefully, don't block
     * application startup or monitoring systems, and provide clear timeout
     * identification in error messages.
     */
    @Test
    void testHealthCheckTimeout() {
        // Given: Hyperion service has connectivity issues causing timeout
        hyperionRequestMockProvider.mockHealthCheckTimeout();

        // When: Health check experiences connection timeout
        long startTime = System.currentTimeMillis();
        Health health = hyperionHealthIndicator.health();
        long duration = System.currentTimeMillis() - startTime;

        // Then: Verify timeout results in DOWN status and completes quickly
        assertThat(health.getStatus()).as("Connection timeout should result in DOWN health status").isEqualTo(Status.DOWN);

        // Verify health check doesn't block for extended periods
        assertThat(duration).as("Health check must complete quickly to avoid blocking monitoring systems").isLessThan(15000); // Should respect configured timeouts

        // Verify timeout is clearly identified in error message
        Map<String, Object> details = health.getDetails();
        assertThat(details.get("error").toString()).as("Timeout errors should be clearly identified for troubleshooting").containsIgnoringCase("timeout");

        hyperionRequestMockProvider.verify();
    }
}
