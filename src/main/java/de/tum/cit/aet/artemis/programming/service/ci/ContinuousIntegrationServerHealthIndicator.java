package de.tum.cit.aet.artemis.programming.service.ci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(PROFILE_CORE)
@Component
@Lazy
public class ContinuousIntegrationServerHealthIndicator implements HealthIndicator {

    private final Optional<StatelessCIService> continuousIntegrationService;

    public ContinuousIntegrationServerHealthIndicator(Optional<StatelessCIService> continuousIntegrationService) {
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
