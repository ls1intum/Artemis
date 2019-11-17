package de.tum.in.www1.artemis.service.connectors;

import java.util.Map;

import org.springframework.boot.actuate.health.Health;

public class ConnectorHealth {

    private boolean isUp;

    private Map<String, Object> additionalInfo;

    private Exception exception;

    public ConnectorHealth(boolean isUp) {
        this.isUp = isUp;
    }

    public ConnectorHealth(boolean isUp, Map<String, Object> additionalInfo) {
        this.isUp = isUp;
        this.additionalInfo = additionalInfo;
    }

    public ConnectorHealth(Exception exception) {
        this.exception = exception;
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

    public boolean isUp() {
        return isUp;
    }

    public void setUp(boolean up) {
        isUp = up;
    }

    public Map<String, Object> getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(Map<String, Object> additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }
}
