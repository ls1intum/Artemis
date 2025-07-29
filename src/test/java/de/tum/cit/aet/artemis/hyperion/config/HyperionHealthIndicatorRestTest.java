package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.hyperion.AbstractHyperionRestTest;

@TestPropertySource(properties = { "artemis.hyperion.url=http://localhost:8080", "artemis.hyperion.api-key=test-api-key" })

class HyperionHealthIndicatorRestTest extends AbstractHyperionRestTest {

    @Autowired
    private HyperionHealthIndicator hyperionHealthIndicator;

    @Test
    void healthUp() {
        hyperionRequestMockProvider.mockHealthCheckSuccess();
        final Health health = hyperionHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("status")).isEqualTo("UP");
        assertThat(health.getDetails().get("version")).isEqualTo("1.0.0");
        hyperionRequestMockProvider.verify();
    }

    @Test
    void healthDown() {
        hyperionRequestMockProvider.mockHealthCheckFailure();
        final Health health = hyperionHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("status")).isEqualTo("DOWN");
        hyperionRequestMockProvider.verify();
    }

    @Test
    void healthNetworkError() {
        hyperionRequestMockProvider.mockHealthStatusNetworkError();
        final Health health = hyperionHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("status")).isEqualTo("DOWN");
        hyperionRequestMockProvider.verify();
    }

    @Test
    void healthTimeout() {
        hyperionRequestMockProvider.mockHealthCheckTimeout();
        final Health health = hyperionHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("status")).isEqualTo("DOWN");
        hyperionRequestMockProvider.verify();
    }
}
