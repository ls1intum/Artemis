package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AthenaRequestMockProvider;

class AthenaHealthIndicatorTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String GREEN_CIRCLE = "\uD83D\uDFE2"; // unicode green circle

    private static final String RED_CIRCLE = "\uD83D\uDD34"; // unicode red circle

    @Autowired
    private AthenaRequestMockProvider athenaRequestMockProvider;

    @Autowired
    private AthenaHealthIndicator athenaHealthIndicator;

    @BeforeEach
    void initTestCase() {
        athenaRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
    }

    @Test
    void healthUp() {
        athenaRequestMockProvider.mockHealthStatusSuccess(true);
        final Health health = athenaHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("module_example").toString()).contains(GREEN_CIRCLE);
    }

    @Test
    void healthUpExampleModuleDown() {
        athenaRequestMockProvider.mockHealthStatusSuccess(false);
        final Health health = athenaHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("module_example").toString()).contains(RED_CIRCLE);
    }

    @Test
    void healthDown() {
        athenaRequestMockProvider.mockHealthStatusFailure();
        final Health health = athenaHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
