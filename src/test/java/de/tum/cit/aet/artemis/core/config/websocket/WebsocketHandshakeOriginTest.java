package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.core.config.websocket.WebsocketConfiguration.isAllowedOrigin;
import static de.tum.cit.aet.artemis.core.config.websocket.WebsocketConfiguration.normalizeOrigin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.socket.WebSocketHandler;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

/**
 * Checks which pages may open a websocket connection, with the CORS configuration of a production server, which allows no other origins.
 */
class WebsocketHandshakeOriginTest {

    private static final String SERVER_ORIGIN = "https://artemis.example.org";

    @Test
    void testOriginsOfArtemisPagesAreAllowed() {
        var noCors = new CorsConfiguration();
        // clients that are not browsers send no origin
        assertThat(isAllowedOrigin(null, "artemis.example.org", SERVER_ORIGIN, noCors)).isTrue();
        // the page was loaded from the host the handshake was sent to, on any port and in any spelling
        assertThat(isAllowedOrigin("https://artemis.example.org", "artemis.example.org", SERVER_ORIGIN, noCors)).isTrue();
        assertThat(isAllowedOrigin("https://Artemis.Example.org", "artemis.example.org", SERVER_ORIGIN, noCors)).isTrue();
        assertThat(isAllowedOrigin("http://localhost:9000", "localhost", SERVER_ORIGIN, noCors)).isTrue();
        assertThat(isAllowedOrigin("https://other-name.example.org", "other-name.example.org", SERVER_ORIGIN, noCors)).isTrue();
        // a proxy that replaces the Host header, and the origin of server.url written with its default port
        assertThat(isAllowedOrigin("https://artemis.example.org", "artemis-app", SERVER_ORIGIN, noCors)).isTrue();
        assertThat(isAllowedOrigin("https://artemis.example.org:443", "artemis-app", SERVER_ORIGIN, noCors)).isTrue();
    }

    @Test
    void testOriginsOfOtherSitesAreRefused() {
        var noCors = new CorsConfiguration();
        for (String origin : List.of("https://evil.example", "https://artemis.example.org.evil.example", "https://evil.example:443", "null", "not a url", "")) {
            assertThat(isAllowedOrigin(origin, "artemis.example.org", SERVER_ORIGIN, noCors)).as(origin).isFalse();
        }
        // without a Host header, only server.url counts
        assertThat(isAllowedOrigin("https://evil.example", null, SERVER_ORIGIN, noCors)).isFalse();
        // http is another origin than the https of server.url, unless the page came from the host the handshake was sent to
        assertThat(isAllowedOrigin("http://artemis.example.org", "artemis-app", SERVER_ORIGIN, noCors)).isFalse();
        // an unusable server.url allows nothing extra
        assertThat(isAllowedOrigin("https://evil.example", "artemis-app", "", noCors)).isFalse();
    }

    @Test
    void testOriginsAllowedForTheRestApiAreAllowed() {
        var cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("https://*.example.edu"));
        assertThat(isAllowedOrigin("https://client.example.edu", "artemis-app", SERVER_ORIGIN, cors)).isTrue();
        assertThat(isAllowedOrigin("https://evil.example", "artemis-app", SERVER_ORIGIN, cors)).isFalse();
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
    void testHandshakeFromAnotherSiteIsRefusedBeforeTheToken() throws Exception {
        TokenProvider tokenProvider = mock(TokenProvider.class);
        when(tokenProvider.validateTokenForAuthority(anyString(), anyString())).thenReturn(true);

        var foreignSite = new MockHttpServletResponse();
        assertThat(handshake(tokenProvider, "https://evil.example", foreignSite)).isFalse();
        assertThat(foreignSite.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());

        assertThat(handshake(tokenProvider, SERVER_ORIGIN, new MockHttpServletResponse())).isTrue();
        assertThat(handshake(tokenProvider, null, new MockHttpServletResponse())).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static boolean handshake(TokenProvider tokenProvider, @Nullable String origin, MockHttpServletResponse servletResponse) throws Exception {
        var properties = new ArtemisProperties();
        properties.setCors(new CorsConfiguration());
        var configuration = new WebsocketConfiguration(JsonMapper.builder().build(), mock(TaskScheduler.class), tokenProvider, mock(ObjectProvider.class), SERVER_ORIGIN + "/",
                properties);

        var request = new MockHttpServletRequest("GET", "/websocket/websocket");
        request.addHeader(HttpHeaders.HOST, "artemis-app:8080");
        if (origin != null) {
            request.addHeader(HttpHeaders.ORIGIN, origin);
        }
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header.payload.signature");
        return configuration.httpSessionHandshakeInterceptor().beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class), new HashMap<>());
    }
}
