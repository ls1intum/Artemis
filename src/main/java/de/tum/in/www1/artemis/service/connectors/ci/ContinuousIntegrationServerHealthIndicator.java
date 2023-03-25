package de.tum.in.www1.artemis.service.connectors.ci;

import java.util.Optional;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ContinuousIntegrationServerHealthIndicator implements HealthIndicator {

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    public ContinuousIntegrationServerHealthIndicator(Optional<ContinuousIntegrationService> continuousIntegrationService) {
        this.continuousIntegrationService = continuousIntegrationService;
    }

    @Override
    public Health health() {
        if (continuousIntegrationService.isEmpty()) {
            return Health.down(new IllegalStateException("No active Spring profile providing a continuous integration service")).build();
        }
        return continuousIntegrationService.get().health().asActuatorHealth();
    }
}
