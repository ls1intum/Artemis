package de.tum.cit.aet.artemis.deimos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpServer;

import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmRequest;
import de.tum.cit.aet.artemis.deimos.exception.DeimosConfigurationException;
import de.tum.cit.aet.artemis.deimos.exception.DeimosLlmException;
import de.tum.cit.aet.artemis.deimos.service.DefaultDeimosLlmClient;

class DeimosLlmConfigurationTest {

    @Test
    void mapsBaseUrlAndAbsoluteCompletionsPathToThePrefix() {
        assertThat(DeimosLlmConfiguration.toOpenAiCompatibleBaseUrl("https://llm.example.com", "/api/chat/completions")).isEqualTo("https://llm.example.com/api");
    }

    @Test
    void preservesAnExistingBasePathWhenMappingTheCompletionsPath() {
        // Plain concatenation rather than URI.resolve, so a base URL that already carries a path keeps it.
        assertThat(DeimosLlmConfiguration.toOpenAiCompatibleBaseUrl("https://llm.example.com/v1", "/api/chat/completions")).isEqualTo("https://llm.example.com/v1/api");
    }

    @Test
    void stripsATrailingSlashFromTheBaseUrlBeforeConcatenating() {
        assertThat(DeimosLlmConfiguration.toOpenAiCompatibleBaseUrl("https://llm.example.com/", "/chat/completions")).isEqualTo("https://llm.example.com");
    }

    @Test
    void rejectsARelativeCompletionsPathThatWouldCorruptTheHost() {
        // Without the absolute-path check this produced "https://llm.example.comapi", silently targeting a wrong host.
        assertThatExceptionOfType(DeimosConfigurationException.class)
                .isThrownBy(() -> DeimosLlmConfiguration.toOpenAiCompatibleBaseUrl("https://llm.example.com", "api/chat/completions"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/chat/completions", "/chat/completions", "/deep/nested/api/chat/completions" })
    void acceptsAbsolutePathsEndingWithTheChatCompletionsSuffix(String completionsPath) {
        assertThat(DeimosLlmConfiguration.isValidCompletionsPath(completionsPath)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "api/chat/completions", "/api/completions", "/api/chat/completions/", "http://x/api/chat/completions", "//host/api/chat/completions",
            "/api/chat/completions?x=1", "/api/chat/completions#frag", "" })
    void rejectsRelativeSchemedOrOtherwiseUnmappablePaths(String completionsPath) {
        assertThat(DeimosLlmConfiguration.isValidCompletionsPath(completionsPath)).isFalse();
    }

    @Test
    void rejectsANullCompletionsPath() {
        assertThat(DeimosLlmConfiguration.isValidCompletionsPath(null)).isFalse();
    }

    @Test
    void reportsAnEmptyApiKeyRejectedByTheEndpointAsRejectedCredentials() throws Exception {
        List<String> authorizationHeaders = callRejectingEndpointWith("");

        // An empty key means no Authorization header at all, which a key-protected endpoint answers with 401.
        assertThat(authorizationHeaders).containsExactly("<none>");
    }

    @Test
    void reportsAWrongApiKeyRejectedByTheEndpointAsRejectedCredentials() throws Exception {
        List<String> authorizationHeaders = callRejectingEndpointWith("wrong-key");

        assertThat(authorizationHeaders).containsExactly("Bearer wrong-key");
    }

    /**
     * Sends one analysis request through the real Deimos chat model to a loopback endpoint that answers every request
     * with 401, and asserts that the failure is reported as rejected credentials.
     *
     * @param apiKey the configured API key
     * @return the Authorization header of every request the endpoint received, {@code <none>} when absent
     */
    private static List<String> callRejectingEndpointWith(String apiKey) throws Exception {
        List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            authorizationHeaders.add(authorization != null ? authorization : "<none>");
            byte[] body = "{\"error\":{\"message\":\"Invalid API key\",\"type\":\"invalid_request_error\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, body.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        });
        server.start();
        try {
            var configuration = new DeimosLlmConfiguration();
            String baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
            var chatModel = configuration.deimosChatModel(baseUrl, apiKey, "/api/chat/completions", "test-model", 0, 10, 0);
            var client = new DefaultDeimosLlmClient(configuration.deimosChatClient(chatModel));

            assertThatExceptionOfType(DeimosLlmException.class).isThrownBy(() -> client.analyze(new DeimosLlmRequest(1L, "system", "user")))
                    .satisfies(exception -> assertThat(exception.getFailureType()).isEqualTo(DeimosFailureType.LLM_AUTHENTICATION_FAILED)).withMessageContaining("HTTP 401")
                    .withNoCause();
            return authorizationHeaders;
        }
        finally {
            server.stop(0);
        }
    }
}
