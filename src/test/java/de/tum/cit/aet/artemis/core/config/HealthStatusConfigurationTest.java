package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.health.actuate.endpoint.SimpleHttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.SimpleStatusAggregator;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthProperties;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;

/**
 * Verifies the status ranking and the HTTP mapping that {@code application.yml} configures for {@code /management/health}, using the same classes
 * Spring Boot builds the health endpoint from. They decide what the aggregate health reports when an optional external system is not working (#14680).
 */
class HealthStatusConfigurationTest {

    // The test classpath has an application.yml of its own that shadows the one of the application, so the application's file is read from the sources.
    private static final Resource MAIN_APPLICATION_YML = new FileSystemResource("src/main/resources/config/application.yml");

    private static SimpleStatusAggregator aggregator;

    private static SimpleHttpCodeStatusMapper httpMapper;

    @BeforeAll
    static void loadConfiguredStatusHandling() throws IOException {
        var sources = new MutablePropertySources();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("application", MAIN_APPLICATION_YML)) {
            sources.addLast(source);
        }
        HealthProperties.Status status = new Binder(ConfigurationPropertySources.from(sources)).bind("management.endpoint.health.status", HealthProperties.Status.class).get();
        aggregator = new SimpleStatusAggregator(status.getOrder());
        httpMapper = new SimpleHttpCodeStatusMapper(status.getHttpMapping());
    }

    @Test
    void degradedOptionalSystemKeepsTheAggregateAtHttp200() {
        Status aggregate = aggregator.getAggregateStatus(Set.of(Status.UP, ConnectorHealth.DEGRADED));

        assertThat(aggregate).isEqualTo(ConnectorHealth.DEGRADED);
        assertThat(httpMapper.getStatusCode(aggregate)).isEqualTo(200);
    }

    @Test
    void downOfACriticalComponentStillWinsOverDegraded() {
        Status aggregate = aggregator.getAggregateStatus(Set.of(Status.UP, ConnectorHealth.DEGRADED, Status.DOWN));

        assertThat(aggregate).isEqualTo(Status.DOWN);
        assertThat(httpMapper.getStatusCode(aggregate)).isEqualTo(503);
    }

    @Test
    void outOfServiceStillRanksAboveDegraded() {
        Status aggregate = aggregator.getAggregateStatus(Set.of(ConnectorHealth.DEGRADED, Status.OUT_OF_SERVICE));

        assertThat(aggregate).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(httpMapper.getStatusCode(aggregate)).isEqualTo(503);
    }

    @Test
    void everythingUpStaysUp() {
        Status aggregate = aggregator.getAggregateStatus(Set.of(Status.UP));

        assertThat(aggregate).isEqualTo(Status.UP);
        assertThat(httpMapper.getStatusCode(aggregate)).isEqualTo(200);
    }
}
