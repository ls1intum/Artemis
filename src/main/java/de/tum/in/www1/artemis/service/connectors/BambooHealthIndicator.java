package de.tum.in.www1.artemis.service.connectors;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("bamboo")
@Component
public class BambooHealthIndicator implements HealthIndicator {

    private final BambooService bambooService;

    public BambooHealthIndicator(BambooService bambooService) {
        this.bambooService = bambooService;
    }

    @Override
    public Health health() {
        return bambooService.health().asActuatorHealth();
    }
}
