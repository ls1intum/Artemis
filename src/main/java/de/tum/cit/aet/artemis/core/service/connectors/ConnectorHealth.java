package de.tum.cit.aet.artemis.core.service.connectors;

import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * The outcome of checking one connector, convertible into an actuator {@link Health}.
 * <p>
 * A connector is either <b>critical</b> or <b>optional</b>, and the choice decides what a failed check means for the whole instance:
 * <ul>
 * <li>A critical component (the database, the distributed data provider, the websocket broker, the service registry, the continuous integration
 * server) that fails makes the instance unable to do its job. It reports {@code DOWN}, for the connectors in this package and the websocket broker
 * through {@link #asActuatorHealth()}, so the aggregate {@code /management/health} answers with HTTP 503.</li>
 * <li>An optional external system (Athena, Pyris, Hermes, Apollon, Weaviate, the sharing platform, the Hyperion workers) that fails leaves the core
 * of Artemis working. It reports {@link #DEGRADED}, through {@link #asOptionalActuatorHealth()} or directly with {@code Health.status(DEGRADED)}.
 * The aggregate then reads {@code DEGRADED}, but still
 * answers with HTTP 200, so a container health check or load balancer does not restart or remove an instance because a service it
 * does not even depend on is in maintenance. The failing component and its details stay visible on the health page and in the
 * {@code artemis.health} metric.</li>
 * </ul>
 * The ranking of the statuses and the HTTP mapping of {@link #DEGRADED} are configured in {@code application.yml} under
 * {@code management.endpoint.health.status}.
 */
public record ConnectorHealth(boolean isUp, Map<String, Object> additionalInfo, Exception exception) {

    /**
     * The status of an optional external system that cannot be reached or reports itself as not working. It ranks between {@code OUT_OF_SERVICE} and
     * {@code UP}, so a {@code DOWN} of a critical component always wins in the aggregate, and it maps to HTTP 200.
     */
    public static final Status DEGRADED = new Status("DEGRADED", "An optional external system is not available, the core functionality is not affected");

    public ConnectorHealth(boolean isUp, Map<String, Object> additionalInfo) {
        this(isUp, additionalInfo, null);
    }

    public ConnectorHealth(Exception exception) {
        this(false, null, exception);
    }

    /**
     * Converts the health instance of a <b>critical</b> connector into a for the Spring Boot actuator readable health object. This can then be
     * exposed on the /health endpoint using a custom {@link org.springframework.boot.health.contributor.HealthIndicator}. A failed
     * check reports {@code DOWN}.
     *
     * @return The health object to be exposed in a HealthIndicator
     */
    public Health asActuatorHealth() {
        return toActuatorHealth(Health.down());
    }

    /**
     * Converts the health instance of an <b>optional</b> external system into an actuator health object. A failed check reports
     * {@link #DEGRADED} instead of {@code DOWN}, so that the outage of a system the core does not depend on does not make the whole instance unhealthy.
     *
     * @return The health object to be exposed in a HealthIndicator
     */
    public Health asOptionalActuatorHealth() {
        return toActuatorHealth(Health.status(DEGRADED));
    }

    private Health toActuatorHealth(Health.Builder failed) {
        final var health = this.isUp ? Health.up() : failed;
        if (this.exception != null) {
            health.withException(this.exception);
        }
        if (this.additionalInfo != null) {
            health.withDetails(this.additionalInfo);
        }

        return health.build();
    }
}
