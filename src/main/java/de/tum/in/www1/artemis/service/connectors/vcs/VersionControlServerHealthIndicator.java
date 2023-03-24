package de.tum.in.www1.artemis.service.connectors.vcs;

import java.util.Optional;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

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
