package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.hyperion.AbstractHyperionIntegrationTest;

/**
 * Integration tests for Hyperion health indicator.
 */
@SpringBootTest
@Profile(PROFILE_HYPERION)
@Import(HyperionTestConfiguration.class)
class HyperionHealthIndicatorTest extends AbstractHyperionIntegrationTest {

    @Autowired
    private HyperionHealthIndicator hyperionHealthIndicator;

    @Test
    void healthCheck_shouldReturnUp() {
        var health = hyperionHealthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("host");
        assertThat(health.getDetails()).containsKey("port");
        assertThat(health.getDetails()).containsKey("useTls");
    }
}
