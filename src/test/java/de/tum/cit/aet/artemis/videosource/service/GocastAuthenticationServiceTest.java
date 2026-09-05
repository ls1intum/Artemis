package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GocastAuthenticationServiceTest {

    private static final String BASE_URL = "http://localhost:18081/api/v2";

    private MockRestServiceServer server;

    private MutableClock clock;

    private GocastAuthenticationService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(Instant.parse("2026-09-05T03:00:00Z"));
        service = new GocastAuthenticationService(builder.build(), "artemis@example.org", "secret", clock);
    }

    @Test
    void sharesLoginAcrossConcurrentCallersAndRefreshesBeforeExpiry() {
        expectLogin("first-token", 100, 37);
        expectLogin("second-token", 100, 37);
        CompletableFuture<?>[] callers = IntStream.range(0, 8).mapToObj(ignored -> CompletableFuture.supplyAsync(service::getSession)).toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(callers).join();
        assertThat(callers).allSatisfy(caller -> assertThat(caller.join()).isEqualTo(new GocastAuthenticationService.Session("Bearer first-token", 37)));

        clock.advanceSeconds(91);
        assertThat(service.getSession()).isEqualTo(new GocastAuthenticationService.Session("Bearer second-token", 37));
        server.verify();
    }

    @Test
    void invalidatesOnlyTheMatchingCurrentToken() {
        expectLogin("first-token", 100, 37);
        expectLogin("second-token", 100, 37);
        assertThat(service.getSession().authorizationHeader()).isEqualTo("Bearer first-token");

        service.invalidate("Bearer stale-token");
        assertThat(service.getSession().authorizationHeader()).isEqualTo("Bearer first-token");

        service.invalidate("Bearer first-token");
        assertThat(service.getSession().authorizationHeader()).isEqualTo("Bearer second-token");
        server.verify();
    }

    @Test
    void rejectsMalformedLoginAndPreservesHttpStatus() {
        server.expect(requestTo(BASE_URL + "/integration/login"))
                .andRespond(withSuccess("{\"accessToken\":\"token\",\"tokenType\":\"Bearer\",\"expiresIn\":0,\"userId\":37}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::getSession).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));

        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/login")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(service::getSession).isInstanceOf(GocastIntegrationException.class)
                .satisfies(error -> assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void distinguishesInvalidJsonFromTransportTimeoutWithoutRetainingRawCause() {
        server.expect(requestTo(BASE_URL + "/integration/login")).andRespond(withSuccess("secret-not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::getSession).isInstanceOf(GocastIntegrationException.class).satisfies(error -> {
            assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(error.getCause()).isNull();
        });

        server.reset();
        server.expect(requestTo(BASE_URL + "/integration/login")).andRespond(withException(new SocketTimeoutException("secret timeout detail")));
        assertThatThrownBy(service::getSession).isInstanceOf(GocastIntegrationException.class).satisfies(error -> {
            assertThat(((GocastIntegrationException) error).getUpstreamStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(error.getCause()).isNull();
        });
    }

    private void expectLogin(String token, int expiresIn, long userId) {
        server.expect(requestTo(BASE_URL + "/integration/login")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"email\":\"artemis@example.org\",\"password\":\"secret\"}")).andRespond(withSuccess(
                        "{\"accessToken\":\"%s\",\"tokenType\":\"Bearer\",\"expiresIn\":%d,\"userId\":%d}".formatted(token, expiresIn, userId), MediaType.APPLICATION_JSON));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
