package de.tum.cit.aet.artemis.deimos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Api;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.EndpointType;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Preset;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmRequest;
import de.tum.cit.aet.artemis.deimos.exception.DeimosLlmException;
import de.tum.cit.aet.artemis.deimos.service.DefaultDeimosLlmClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

class DeimosLlmConfigurationTest {

    private static final String PRESET = "deimos";

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
     * Sends one analysis request through the real chat model of the Deimos preset to a loopback endpoint that answers
     * every request with 401, and asserts that the failure is reported as rejected credentials.
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
            String baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
            var endpoint = new Endpoint(EndpointType.OPENAI, baseUrl, apiKey, Duration.ofSeconds(10), 0, null, null, Map.of(), List.of());
            var preset = new Preset(PRESET, "test-model", Api.RESPONSES, null, 0.0, null, null, null, null, null, null);
            var llmClientService = new LlmClientService(new LlmProperties(Map.of(PRESET, endpoint), Map.of(PRESET, preset), null), new JsonMapper(), ObservationRegistry.NOOP,
                    new SimpleMeterRegistry());
            var client = new DefaultDeimosLlmClient(llmClientService, PRESET);

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
