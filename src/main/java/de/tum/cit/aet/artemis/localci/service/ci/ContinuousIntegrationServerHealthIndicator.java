package de.tum.cit.aet.artemis.localci.service.ci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(PROFILE_CORE)
@Component
@Lazy
public class ContinuousIntegrationServerHealthIndicator implements HealthIndicator {

    // ObjectProvider (instead of direct/Optional injection): a health check is request-time, so the CI service graph (build config, script templates, ...) must not be
    // instantiated during eager startup. ObjectProvider resolves the bean lazily on getIfAvailable(). Note: @Lazy on an Optional injection point does NOT defer it, because
    // Spring resolves the Optional eagerly to determine presence.
    private final ObjectProvider<ContinuousIntegrationService> continuousIntegrationService;

    public ContinuousIntegrationServerHealthIndicator(ObjectProvider<ContinuousIntegrationService> continuousIntegrationService) {
        this.continuousIntegrationService = continuousIntegrationService;
    }

    @Override
    public Health health() {
        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.getIfAvailable();
        if (continuousIntegration == null) {
            return Health.down(new IllegalStateException("No active Spring profile providing a continuous integration service")).build();
        }
        return continuousIntegration.health().asActuatorHealth();
    }
}
