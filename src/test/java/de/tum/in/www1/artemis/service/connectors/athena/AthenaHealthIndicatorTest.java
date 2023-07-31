package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class AthenaHealthIndicatorTest extends AthenaTest {

    private final String MODULE = "module_example";

    private static final String GREEN_CIRCLE = "\uD83D\uDFE2"; // unicode green circle

    private static final String RED_CIRCLE = "\uD83D\uDD34"; // unicode red circle

    @Autowired
    private AthenaHealthIndicator athenaHealthIndicator;

    @Test
    void healthUp() {
        athenaRequestMockProvider.mockHealthStatusSuccess(true);
        final Health health = athenaHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get(MODULE).toString()).contains(GREEN_CIRCLE);
    }

    @Test
    void healthUpExampleModuleDown() {
        athenaRequestMockProvider.mockHealthStatusSuccess(false);
        final Health health = athenaHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get(MODULE).toString()).contains(RED_CIRCLE);
    }

    @Test
    void healthDown() {
        athenaRequestMockProvider.mockHealthStatusFailure();
        final Health health = athenaHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
