package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.core.config.websocket.WebsocketConfiguration.isAllowedOrigin;
import static de.tum.cit.aet.artemis.core.config.websocket.WebsocketConfiguration.normalizeOrigin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.socket.WebSocketHandler;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;

/**
 * Checks which pages may open a websocket connection, with the CORS configuration of a production server, which allows no other origins.
 */
class WebsocketHandshakeOriginTest {

    private static final String SERVER_ORIGIN = "https://artemis.example.org";

    @Test
    void testOriginsOfArtemisPagesAreAllowed() {
        var noCors = new CorsConfiguration();
        // clients that are not browsers send no origin
        assertThat(isAllowedOrigin(null, false, SERVER_ORIGIN, noCors)).isTrue();
        // the page has the origin of the handshake request itself, e.g. another host name of Artemis behind a proxy that forwards its headers
        assertThat(isAllowedOrigin("https://other-name.example.org", true, SERVER_ORIGIN, noCors)).isTrue();
        // the origin of server.url, in any spelling
        for (String origin : List.of("https://artemis.example.org", "https://Artemis.Example.org", "https://artemis.example.org:443")) {
            assertThat(isAllowedOrigin(origin, false, SERVER_ORIGIN, noCors)).as(origin).isTrue();
        }
    }

    @Test
    void testOriginsOfOtherPagesAreRefused() {
        var noCors = new CorsConfiguration();
        for (String origin : List.of("https://evil.example", "https://artemis.example.org.evil.example", "https://artemis.example.org:8443", "http://artemis.example.org", "null",
                "not a url", "")) {
            assertThat(isAllowedOrigin(origin, false, SERVER_ORIGIN, noCors)).as(origin).isFalse();
        }
        // an unusable server.url allows nothing extra
        assertThat(isAllowedOrigin("https://evil.example", false, "", noCors)).isFalse();
    }

    @Test
    void testOriginsAllowedForTheRestApiAreAllowed() {
        var cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("https://*.example.edu"));
        assertThat(isAllowedOrigin("https://client.example.edu", false, SERVER_ORIGIN, cors)).isTrue();
        assertThat(isAllowedOrigin("https://evil.example", false, SERVER_ORIGIN, cors)).isFalse();
    }

    @Test
    void testNormalizeOrigin() {
        assertThat(normalizeOrigin("https://Artemis.Example.org:443/")).isEqualTo("https://artemis.example.org");
        assertThat(normalizeOrigin("http://localhost:80")).isEqualTo("http://localhost");
        assertThat(normalizeOrigin("http://localhost:8080/path")).isEqualTo("http://localhost:8080");
        assertThat(normalizeOrigin(" https://artemis.example.org ")).isEqualTo("https://artemis.example.org");
        for (String unusable : new String[] { null, "", "artemis.example.org", "not a url" }) {
            assertThat(normalizeOrigin(unusable)).as(String.valueOf(unusable)).isEmpty();
        }
    }

    @Test
    void testHandshakeIsCheckedAgainstTheOriginOfTheRequest() throws Exception {
        TokenProvider tokenProvider = mock(TokenProvider.class);
        when(tokenProvider.validateTokenForAuthority(anyString(), anyString())).thenReturn(true);

        // as the request arrives from a proxy that forwards Host and X-Forwarded-Proto
        assertThat(handshake(tokenProvider, "https", "artemis.example.org", 443, "https://artemis.example.org")).isEqualTo(HandshakeOutcome.ACCEPTED);
        assertThat(handshake(tokenProvider, "https", "other-name.example.org", 443, "https://other-name.example.org")).isEqualTo(HandshakeOutcome.ACCEPTED);
        assertThat(handshake(tokenProvider, "https", "artemis.example.org", 443, null)).isEqualTo(HandshakeOutcome.ACCEPTED);
        // another port or scheme of the same host is another origin
        assertThat(handshake(tokenProvider, "https", "artemis.example.org", 443, "https://artemis.example.org:8443")).isEqualTo(HandshakeOutcome.REFUSED);
        assertThat(handshake(tokenProvider, "https", "artemis.example.org", 443, "http://artemis.example.org")).isEqualTo(HandshakeOutcome.REFUSED);
        assertThat(handshake(tokenProvider, "https", "artemis.example.org", 443, "https://evil.example")).isEqualTo(HandshakeOutcome.REFUSED);

        // a proxy whose forwarded headers are not used: only the origin of server.url matches
        assertThat(handshake(tokenProvider, "http", "artemis-app", 8080, "https://artemis.example.org")).isEqualTo(HandshakeOutcome.ACCEPTED);
        assertThat(handshake(tokenProvider, "http", "artemis-app", 8080, "https://other-name.example.org")).isEqualTo(HandshakeOutcome.REFUSED);
    }

    private enum HandshakeOutcome {
        ACCEPTED, REFUSED
    }

    @SuppressWarnings("unchecked")
    private static HandshakeOutcome handshake(TokenProvider tokenProvider, String scheme, String host, int port, @Nullable String origin) throws Exception {
        var properties = new ArtemisProperties();
        properties.setCors(new CorsConfiguration());
        var configuration = new WebsocketConfiguration(new MappingJackson2HttpMessageConverter(), mock(TaskScheduler.class), tokenProvider,
                mock(StudentParticipationRepository.class), mock(AuthorizationCheckService.class), mock(ExerciseRepository.class), Optional.empty(), mock(ObjectProvider.class),
                mock(ObjectProvider.class), mock(ObjectProvider.class), mock(ObjectProvider.class), mock(ObjectProvider.class), SERVER_ORIGIN + "/", properties);

        var request = new MockHttpServletRequest("GET", "/websocket/websocket");
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        request.addHeader(HttpHeaders.HOST, host);
        if (origin != null) {
            request.addHeader(HttpHeaders.ORIGIN, origin);
        }
        // a valid token, so that only the origin decides
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header.payload.signature");
        var response = new MockHttpServletResponse();
        boolean accepted = configuration.httpSessionHandshakeInterceptor().beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response),
                mock(WebSocketHandler.class), new HashMap<>());
        if (accepted) {
            return HandshakeOutcome.ACCEPTED;
        }
        assertThat(response.getStatus()).as("a refused origin is answered with 403").isEqualTo(HttpStatus.FORBIDDEN.value());
        return HandshakeOutcome.REFUSED;
    }
}
