package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AthenaRequestMockProvider;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaHealthIndicator;

class AthenaHealthIndicatorTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
        athenaRequestMockProvider.mockQueueStatus(true);
        final Health health = athenaHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void healthDown() {
        athenaRequestMockProvider.mockQueueStatus(false);
        final Health health = athenaHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
