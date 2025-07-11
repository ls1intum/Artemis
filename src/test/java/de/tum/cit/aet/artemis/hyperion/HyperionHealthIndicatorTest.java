package de.tum.cit.aet.artemis.hyperion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Import;

import de.tum.cit.aet.artemis.hyperion.config.HyperionHealthIndicator;
import de.tum.cit.aet.artemis.hyperion.config.HyperionTestConfiguration;
import de.tum.cit.aet.artemis.hyperion.config.HyperionTestHealthService;
import io.grpc.health.v1.HealthCheckResponse;

/**
 * Integration tests for HyperionHealthIndicator.
 * Tests real gRPC health check interactions using an in-process gRPC server.
 */
@Import(HyperionTestConfiguration.class)
class HyperionHealthIndicatorTest extends AbstractHyperionTest {

    @Autowired
    private HyperionHealthIndicator hyperionHealthIndicator;

    @Autowired
    private HyperionTestHealthService testHealthService;

    @BeforeEach
    void setUp() {
        testHealthService.reset();
    }

    @Test
    void testHealthIndicatorWhenServiceIsHealthy() {
        // Given - service is healthy (default state)
        testHealthService.setServingStatus(HealthCheckResponse.ServingStatus.SERVING);

        // When
        Health health = hyperionHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("grpcStatus", "SERVING").containsEntry("url", "localhost:50051").containsEntry("security", "plaintext (⚠️ insecure)");
    }

    @Test
    void testHealthIndicatorWhenServiceIsNotServing() {
        // Given - service is not serving
        testHealthService.setServingStatus(HealthCheckResponse.ServingStatus.NOT_SERVING);

        // When
        Health health = hyperionHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("grpcStatus", "NOT_SERVING").containsEntry("url", "localhost:50051").containsEntry("security", "plaintext (⚠️ insecure)");
    }

    @Test
    void testHealthIndicatorWhenServiceIsUnknown() {
        // Given - service status is unknown
        testHealthService.setServingStatus(HealthCheckResponse.ServingStatus.UNKNOWN);

        // When
        Health health = hyperionHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("grpcStatus", "UNKNOWN").containsEntry("url", "localhost:50051").containsEntry("security", "plaintext (⚠️ insecure)");
    }
}
