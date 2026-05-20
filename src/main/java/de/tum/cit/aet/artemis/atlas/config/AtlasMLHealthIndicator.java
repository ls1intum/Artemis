package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
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

    private final AtlasMLService atlasMLService;

    public AtlasMLHealthIndicator(AtlasMLService atlasMLService) {
        this.atlasMLService = atlasMLService;
    }

    @Override
    public Health health() {
        return atlasMLService.health().asActuatorHealth();
    }
}
