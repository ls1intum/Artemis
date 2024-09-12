package de.tum.cit.aet.artemis.programming.service.vcs;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(PROFILE_CORE)
@Component
public class VersionControlServerHealthIndicator implements HealthIndicator {

    private final Optional<VersionControlService> versionControlService;

    public VersionControlServerHealthIndicator(Optional<VersionControlService> versionControlService) {
        this.versionControlService = versionControlService;
    }

    @Override
    public Health health() {
        if (versionControlService.isEmpty()) {
            return Health.down(new IllegalStateException("No active Spring profile providing a version control service")).build();
        }
        return versionControlService.get().health().asActuatorHealth();
    }
}
