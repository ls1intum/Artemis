package de.tum.cit.aet.artemis.service.connectors.ci;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(PROFILE_CORE)
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
