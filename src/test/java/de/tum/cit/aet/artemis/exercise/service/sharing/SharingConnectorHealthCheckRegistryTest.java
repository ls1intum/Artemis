package de.tum.cit.aet.artemis.exercise.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SharingConnectorHealthCheckRegistryTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    @SuppressWarnings("PMD.ImmutableField")
    private SharingHealthIndicator sharingHealthIndicator;

    @BeforeEach
    void initTestCase() {
    }

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
    void healthDown() throws Exception {
        sharingPlatformMockProvider.mockStatus(false);

        final Health health = sharingHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
