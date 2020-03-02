package de.tum.in.www1.artemis.service.connectors;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;

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
