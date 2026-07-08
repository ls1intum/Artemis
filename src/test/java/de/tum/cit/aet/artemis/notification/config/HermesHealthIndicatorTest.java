package de.tum.cit.aet.artemis.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for {@link HermesHealthIndicator}.
 * <p>
 * Verify that the verdict is based solely on the relay's {@code /api/health} endpoint and that the indicator
 * reports {@code DOWN} both when that endpoint fails (no false-positive {@code UP} from a base-URL fallback)
 * and when the relay reports a disconnected push provider (e.g. a missing/expired APNs certificate).
 * </p>
 */
class HermesHealthIndicatorTest {

    private static final String HERMES_URL = "https://hermes-test.example.com";

    private MockRestServiceServer mockServer;

    private HermesHealthIndicator hermesHealthIndicator;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        hermesHealthIndicator = new HermesHealthIndicator(restTemplate);
        ReflectionTestUtils.setField(hermesHealthIndicator, "hermesUrl", HERMES_URL);
    }

    @Test
    void healthUp_whenAllProvidersConnected() {
        mockServer.expect(requestTo(HERMES_URL + "/api/health"))
                .andRespond(withSuccess("{\"isApnsConnected\":true,\"isFirebaseConnected\":true,\"versionNumber\":\"1.2.3\"}", MediaType.APPLICATION_JSON));

        Health health = hermesHealthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("firebase_up", "up").containsEntry("apns_up", "up").containsEntry("version", "1.2.3").containsEntry("url", HERMES_URL);
        mockServer.verify();
    }

    @Test
    void healthDown_whenApnsDisconnected() {
        // Reproduces a missing/expired APNs certificate on the Hermes host: the relay still answers 2xx but
        // cannot deliver, so the indicator must report DOWN rather than hide the outage behind UP.
        mockServer.expect(requestTo(HERMES_URL + "/api/health"))
                .andRespond(withSuccess("{\"isApnsConnected\":false,\"isFirebaseConnected\":true,\"versionNumber\":\"1.2.3\"}", MediaType.APPLICATION_JSON));

        Health health = hermesHealthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("apns_up", "down").containsEntry("firebase_up", "up");
        mockServer.verify();
    }

    @Test
    void healthUp_whenFlagsAbsent_doesNotPenalize() {
        // A relay that does not report the connectivity flags (or adds unknown fields) must not flip to DOWN:
        // only an explicit `false` marks a provider as disconnected.
        mockServer.expect(requestTo(HERMES_URL + "/api/health")).andRespond(withSuccess("{\"versionNumber\":\"1.2.3\",\"newUnknownField\":42}", MediaType.APPLICATION_JSON));

        Health health = hermesHealthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("version", "1.2.3").doesNotContainKey("apns_up");
        mockServer.verify();
    }

    @Test
    void healthDown_onServerError() {
        mockServer.expect(requestTo(HERMES_URL + "/api/health")).andRespond(withServerError());

        Health health = hermesHealthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error").containsEntry("url", HERMES_URL);
        mockServer.verify();
    }

    @Test
    void healthDown_onTimeout_withoutBaseUrlFallback() {
        // Reproduces the hung /api/health observed in production: the request times out. Only /api/health is
        // expected, so if the indicator fell back to pinging the base URL this test would fail on an unexpected request.
        mockServer.expect(requestTo(HERMES_URL + "/api/health")).andRespond(withException(new SocketTimeoutException("Read timed out")));

        Health health = hermesHealthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        mockServer.verify();
    }
}
