package de.tum.cit.aet.artemis.atlas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.atlas.service.atlasml.AtlasMLService;

/**
 * Health indicator for the AtlasML microservice.
 * Reports the health status of the AtlasML service to Spring Boot Actuator.
 */
@Conditional(AtlasMLEnabled.class)
@Component
@Lazy
public class AtlasMLHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AtlasMLHealthIndicator.class);

    private final AtlasMLService atlasMLService;

    public AtlasMLHealthIndicator(AtlasMLService atlasMLService) {
        this.atlasMLService = atlasMLService;
    }

    @Override
    public Health health() {
        try {
            boolean isHealthy = atlasMLService.isHealthy();

            if (isHealthy) {
                log.debug("AtlasML health check passed");
                return Health.up().withDetail("service", "AtlasML").withDetail("status", "UP").build();
            }
            else {
                log.warn("AtlasML health check failed");
                return Health.down().withDetail("service", "AtlasML").withDetail("status", "DOWN").build();
            }
        }
        catch (Exception e) {
            log.error("Error during AtlasML health check", e);
            return Health.down().withDetail("service", "AtlasML").withDetail("status", "DOWN").withDetail("error", e.getMessage()).build();
        }
    }
}
