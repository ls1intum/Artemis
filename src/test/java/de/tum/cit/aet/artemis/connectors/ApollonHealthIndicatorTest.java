package de.tum.cit.aet.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import de.tum.cit.aet.artemis.core.connector.apollon.ApollonRequestMockProvider;
import de.tum.cit.aet.artemis.modeling.service.apollon.ApollonHealthIndicator;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ApollonHealthIndicatorTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ApollonRequestMockProvider apollonRequestMockProvider;

    @Autowired
    private ApollonHealthIndicator apollonHealthIndicator;

    @BeforeEach
    void initTestCase() {
        apollonRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        apollonRequestMockProvider.reset();
    }

    @Test
    void healthUp() {
        apollonRequestMockProvider.mockStatus(true);
        final Health health = apollonHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void healthDown() {
        apollonRequestMockProvider.mockStatus(false);
        final Health health = apollonHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
