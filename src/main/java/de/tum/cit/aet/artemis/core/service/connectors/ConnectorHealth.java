package de.tum.cit.aet.artemis.core.service.connectors;

import java.util.Map;

import org.springframework.boot.actuate.health.Health;

public record ConnectorHealth(boolean isUp, Map<String, Object> additionalInfo, Exception exception) {

    public ConnectorHealth(boolean isUp, Map<String, Object> additionalInfo) {
        this(isUp, additionalInfo, null);
    }

    public ConnectorHealth(Exception exception) {
        this(false, null, exception);
    }

    /**
     * Converts the health instance into a for the Spring Boot actuator readable health object. This can then be
     * exposed on the /health endpoint using a custom {@link org.springframework.boot.actuate.health.HealthIndicator}
     *
     * @return The health object to be exposed in a HealthIndicator
     */
    public Health asActuatorHealth() {
        final var health = this.isUp ? Health.up() : Health.down();
        if (this.exception != null) {
            health.withException(this.exception);
        }
        if (this.additionalInfo != null) {
            health.withDetails(this.additionalInfo);
        }

        return health.build();
    }
}
