package de.tum.cit.aet.artemis.sharing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SharingConnectorHealthCheckRegistryTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private SharingHealthIndicator sharingHealthIndicator;

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
    }

    @Test
    void shouldReportHealthUpWhenSharingPlatformIsConnected() throws Exception {
        // given
        sharingPlatformMockProvider.mockStatus(true);
        // when
        final Health health = sharingHealthIndicator.health();
        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void shouldReportHealthDegradedWhenSharingPlatformIsDisconnected() throws Exception {
        // given
        sharingPlatformMockProvider.mockStatus(false);

        // when
        final Health health = sharingHealthIndicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(ConnectorHealth.DEGRADED);
    }
}
