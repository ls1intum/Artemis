package de.tum.cit.aet.artemis.atlas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.atlas.service.atlasml.AtlasMLService;

/**
 * Health indicator for the AtlasML microservice.
 * Reports the health status of the AtlasML service to Spring Boot Actuator.
 * Uses ObjectProvider to defer AtlasMLService instantiation until the health check is actually called.
 */
@Conditional(AtlasEnabled.class)
// @Component // temporarily disabled
@Lazy
public class AtlasMLHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AtlasMLHealthIndicator.class);

    private final ObjectProvider<AtlasMLService> atlasMLServiceProvider;

    public AtlasMLHealthIndicator(ObjectProvider<AtlasMLService> atlasMLServiceProvider) {
        this.atlasMLServiceProvider = atlasMLServiceProvider;
    }

    @Override
    public Health health() {
        try {
            AtlasMLService atlasMLService = atlasMLServiceProvider.getIfAvailable();
            if (atlasMLService == null) {
                log.warn("AtlasML service is not available");
                return Health.down().withDetail("service", "AtlasML").withDetail("status", "UNAVAILABLE").build();
            }

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
