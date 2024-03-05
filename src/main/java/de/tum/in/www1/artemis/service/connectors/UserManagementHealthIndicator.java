package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;

@Profile(PROFILE_CORE)
@Component
public class UserManagementHealthIndicator implements HealthIndicator {

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    public UserManagementHealthIndicator(ArtemisAuthenticationProvider artemisAuthenticationProvider) {
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
    }

    @Override
    public Health health() {
        return artemisAuthenticationProvider.health().asActuatorHealth();
    }
}
