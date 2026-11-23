package de.tum.cit.aet.artemis.modeling.apollon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.core.connector.apollon.ApollonRequestMockProvider;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
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
    void healthDegraded() {
        apollonRequestMockProvider.mockStatus(false);
        final Health health = apollonHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(ConnectorHealth.DEGRADED);
    }

    @Test
    void healthDegraded_whenConversionUrlIsBlank_withoutThrowing() {
        // The production client converts the URI with toURL(), which throws an IllegalArgumentException for a relative URI. A misconfigured optional system must not fail the
        // health request.
        var indicator = new ApollonHealthIndicator(RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()).build());
        ReflectionTestUtils.setField(indicator, "apollonConversionUrl", "");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(ConnectorHealth.DEGRADED);
        assertThat(health.getDetails().get("error").toString()).contains("URI is not absolute");
    }
}
