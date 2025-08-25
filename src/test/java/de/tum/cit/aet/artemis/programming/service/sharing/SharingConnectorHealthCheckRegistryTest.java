package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SharingConnectorHealthCheckRegistryTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private SharingHealthIndicator sharingHealthIndicator;

    @Autowired
    private SharingConnectorService sharingConnectorService;

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
    void shouldLimitHealthHistorySizeToConfiguredMaximum() throws Exception {
        // given
        sharingPlatformMockProvider.mockStatus(true);

        SharingConnectorService.HealthStatusWithHistory lastHealthStati = sharingConnectorService.getLastHealthStati();

        // when - adding more entries than the limit
        for (int i = 0; i < SharingConnectorService.HEALTH_HISTORY_LIMIT * 2; i++) {
            lastHealthStati.add(new SharingConnectorService.HealthStatus("Just Testing"));
            assertThat(lastHealthStati).size().isLessThanOrEqualTo(SharingConnectorService.HEALTH_HISTORY_LIMIT);
        }

        // then
        assertThat(lastHealthStati).size().isEqualTo(SharingConnectorService.HEALTH_HISTORY_LIMIT);
    }

    @Test
    void shouldReportHealthDownWhenSharingPlatformIsDisconnected() throws Exception {
        // given
        sharingPlatformMockProvider.mockStatus(false);

        // when
        final Health health = sharingHealthIndicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
