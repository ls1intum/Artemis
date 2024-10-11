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
    void healthUp() throws Exception {

        sharingPlatformMockProvider.mockStatus(true);

        final Health health = sharingHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void testMaxStatusInfo() throws Exception {
        sharingPlatformMockProvider.mockStatus(true);

        SharingConnectorService.HealthStatusWithHistory lastHealthStati = sharingConnectorService.getLastHealthStati();

        for (int i = 0; i < SharingConnectorService.HEALTH_HISTORY_LIMIT * 2; i++) {
            lastHealthStati.add(new SharingConnectorService.HealthStatus("Just Testing"));
            assertThat(lastHealthStati).size().isLessThanOrEqualTo(SharingConnectorService.HEALTH_HISTORY_LIMIT);
        }
        assertThat(lastHealthStati).size().isEqualTo(SharingConnectorService.HEALTH_HISTORY_LIMIT);
    }

    @Test
    void healthDown() throws Exception {
        sharingPlatformMockProvider.mockStatus(false);

        final Health health = sharingHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
