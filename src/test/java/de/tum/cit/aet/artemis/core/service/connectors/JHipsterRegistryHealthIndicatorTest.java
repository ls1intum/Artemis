package de.tum.cit.aet.artemis.core.service.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;

/**
 * Unit tests for {@link JHipsterRegistryHealthIndicator}.
 * Tests verify the health reporting for the JHipster Registry (Eureka) connection.
 */
@ExtendWith(MockitoExtension.class)
class JHipsterRegistryHealthIndicatorTest {

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private Registration registration;

    private JHipsterRegistryHealthIndicator jhipsterRegistryHealthIndicator;

    @BeforeEach
    void setUp() {
        jhipsterRegistryHealthIndicator = new JHipsterRegistryHealthIndicator(discoveryClient, Optional.of(registration));
    }

    @Test
    void healthUp_whenConnectedAndRegistered() {
        // Given
        when(discoveryClient.getServices()).thenReturn(List.of("Artemis", "other-service"));
        when(registration.getServiceId()).thenReturn("Artemis");
        when(registration.getInstanceId()).thenReturn("Artemis:1");
        when(registration.getHost()).thenReturn("192.168.1.10");
        when(registration.getPort()).thenReturn(8080);

        ServiceInstance thisInstance = new DefaultServiceInstance("Artemis:1", "Artemis", "192.168.1.10", 8080, false);
        ServiceInstance otherInstance = new DefaultServiceInstance("Artemis:2", "Artemis", "192.168.1.11", 8080, false);
        when(discoveryClient.getInstances("Artemis")).thenReturn(List.of(thisInstance, otherInstance));

        // When
        Health health = jhipsterRegistryHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("knownServices", 2);
        assertThat(health.getDetails()).containsEntry("serviceId", "Artemis");
        assertThat(health.getDetails()).containsEntry("instanceId", "Artemis:1");
        assertThat(health.getDetails()).containsEntry("host", "192.168.1.10");
        assertThat(health.getDetails()).containsEntry("port", 8080);
        assertThat(health.getDetails()).containsEntry("registeredInstances", 2);
        assertThat(health.getDetails()).containsEntry("thisInstanceRegistered", true);
    }

    @Test
    void healthUp_whenConnectedButInstanceNotYetRegistered() {
        // Given - instance is starting up and not yet visible in registry
        when(discoveryClient.getServices()).thenReturn(List.of("Artemis"));
        when(registration.getServiceId()).thenReturn("Artemis");
        when(registration.getInstanceId()).thenReturn("Artemis:1");
        when(registration.getHost()).thenReturn("192.168.1.10");
        when(registration.getPort()).thenReturn(8080);

        // Only other instance is registered, not this one
        ServiceInstance otherInstance = new DefaultServiceInstance("Artemis:2", "Artemis", "192.168.1.11", 8080, false);
        when(discoveryClient.getInstances("Artemis")).thenReturn(List.of(otherInstance));

        // When
        Health health = jhipsterRegistryHealthIndicator.health();

        // Then - still UP because we can connect to Eureka
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("registeredInstances", 1);
        assertThat(health.getDetails()).containsEntry("thisInstanceRegistered", false);
    }

    @Test
    void healthUp_withNoRegistration() {
        // Given - no registration available (standalone mode)
        jhipsterRegistryHealthIndicator = new JHipsterRegistryHealthIndicator(discoveryClient, Optional.empty());
        when(discoveryClient.getServices()).thenReturn(List.of("some-service"));

        // When
        Health health = jhipsterRegistryHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("knownServices", 1);
        assertThat(health.getDetails()).containsEntry("registration", "not available");
    }

    @Test
    void healthDown_whenDiscoveryClientThrowsException() {
        // Given - Eureka server is unreachable
        when(discoveryClient.getServices()).thenThrow(new RuntimeException("Connection refused"));

        // When
        Health health = jhipsterRegistryHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    void healthUp_withEmptyServiceList() {
        // Given - connected but no services registered yet
        when(discoveryClient.getServices()).thenReturn(List.of());
        when(registration.getServiceId()).thenReturn("Artemis");
        when(registration.getInstanceId()).thenReturn("Artemis:1");
        when(registration.getHost()).thenReturn("localhost");
        when(registration.getPort()).thenReturn(8080);
        when(discoveryClient.getInstances("Artemis")).thenReturn(List.of());

        // When
        Health health = jhipsterRegistryHealthIndicator.health();

        // Then - still UP because connection works
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("knownServices", 0);
        assertThat(health.getDetails()).containsEntry("registeredInstances", 0);
        assertThat(health.getDetails()).containsEntry("thisInstanceRegistered", false);
    }
}
