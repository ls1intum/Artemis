package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import io.grpc.ManagedChannel;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;

/**
 * Health indicator for Hyperion gRPC service following Spring Boot Actuator standards.
 */
@Profile(PROFILE_HYPERION)
@Component
@Lazy
public class HyperionHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HyperionHealthIndicator.class);

    private final ManagedChannel grpcChannel;

    public HyperionHealthIndicator(ManagedChannel grpcChannel) {
        this.grpcChannel = grpcChannel;
    }

    /**
     * Performs health check against the Hyperion gRPC service.
     *
     * @return Health status of Hyperion service
     */
    @Override
    public Health health() {
        // Connection details are now managed by gRPC Spring Boot starter configuration
        var additionalInfo = Map.<String, Object>of("service", "Hyperion gRPC");

        ConnectorHealth connectorHealth;
        try {
            // Use gRPC health check protocol (standard for gRPC services)
            var healthStub = HealthGrpc.newBlockingStub(grpcChannel).withDeadlineAfter(5, TimeUnit.SECONDS);

            var healthRequest = HealthCheckRequest.newBuilder().setService("") // Empty string checks overall server health
                    .build();

            var response = healthStub.check(healthRequest);
            boolean isHealthy = response.getStatus() == HealthCheckResponse.ServingStatus.SERVING;

            connectorHealth = new ConnectorHealth(isHealthy, additionalInfo);

            if (isHealthy) {
                log.debug("Hyperion health check passed");
            }
            else {
                log.warn("Hyperion health check failed - service status: {}", response.getStatus());
                connectorHealth = new ConnectorHealth(false, additionalInfo);
            }

        }
        catch (Exception e) {
            log.warn("Hyperion health check failed: {}", e.getMessage());
            connectorHealth = new ConnectorHealth(false, additionalInfo, e);
        }

        return connectorHealth.asActuatorHealth();
    }
}
