package de.tum.cit.aet.artemis.core.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.LlmChatOptions;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Api;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.EndpointType;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Preset;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

/**
 * Exercises the preset factory against an HTTP server that captures what reaches the provider.
 */
class LlmClientServiceTest {

    private static final String RESPONSE = """
            {"id":"resp-test","object":"response","created_at":1,"model":"served-model","status":"completed","error":null,"incomplete_details":null,
             "instructions":null,"metadata":{},"parallel_tool_calls":true,"temperature":null,"tool_choice":"auto","tools":[],"top_p":null,
             "output":[{"type":"message","id":"msg-1","role":"assistant","status":"completed","content":[{"type":"output_text","text":"OK","annotations":[]}]}],
             "usage":{"input_tokens":3,"input_tokens_details":{"cached_tokens":0},"output_tokens":1,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":4}}
            """;

    private static final String EMBEDDINGS = """
            {"object":"list","model":"served-model","data":[{"object":"embedding","index":0,"embedding":[0.5,-0.5]}],"usage":{"prompt_tokens":2,"total_tokens":2}}
            """;

    private final List<Captured> requests = new CopyOnWriteArrayList<>();

    private final ConcurrentLinkedQueue<Integer> statusCodes = new ConcurrentLinkedQueue<>();

    private volatile boolean delayAnswer;

    private volatile boolean trickleAnswer;

    private volatile String retryAfter = "0";

    private HttpServer server;

    private LlmClientService factory;

    private record Captured(String path, String query, @Nullable String authorization, @Nullable String azureKey, @Nullable String organization, @Nullable String customHeader,
            JsonNode body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                var headers = exchange.getRequestHeaders();
                var uri = exchange.getRequestURI();
                var body = new JsonMapper().readTree(exchange.getRequestBody().readAllBytes());
                requests.add(new Captured(uri.getRawPath(), uri.getQuery(), headers.getFirst("Authorization"), headers.getFirst("api-key"), headers.getFirst("OpenAI-Organization"),
                        headers.getFirst("X-Test"), body));
                if (delayAnswer) {
                    try {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                Integer next = statusCodes.poll();
                int status = next == null ? 200 : next;
                String json = status >= 400 ? "{\"error\":\"provider unavailable\"}" : uri.getPath().endsWith("/responses") ? RESPONSE : EMBEDDINGS;
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Retry-After", retryAfter);
                exchange.sendResponseHeaders(status, trickleAnswer ? 0 : bytes.length);
                if (trickleAnswer) {
                    // The stream stays active past the complete request deadline, without ever hitting the socket read timeout.
                    try {
                        for (int i = 0; i < 30; i++) {
                            exchange.getResponseBody().write(' ');
                            exchange.getResponseBody().flush();
                            Thread.sleep(30);
                        }
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void close() {
        if (factory != null) {
            factory.closeConnections();
        }
        server.stop(0);
    }

    @Test
    void responsesPresetSendsSamplingOptionsAndTagsTheResponse() {
        var preset = new Preset("main", "gpt-6-luna", Api.RESPONSES, "none", 0.2, 0.9, null, null, 100, null, null);
        var service = service(Map.of("main", endpoint(EndpointType.OPENAI, "/v1", "secret")), Map.of("fast", preset));

        ChatResponse response = service.chatModel("fast").call(new Prompt("Hello"));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("OK");
        assertThat(LlmClientService.presetOf(response.getMetadata())).isEqualTo("fast");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/v1/responses");
            assertThat(request.authorization()).isEqualTo("Bearer secret");
            assertThat(request.customHeader()).isEqualTo("on");
            assertThat(request.body().get("model").asString()).isEqualTo("gpt-6-luna");
            assertThat(request.body().get("reasoning").get("effort").asString()).isEqualTo("none");
            assertThat(request.body().get("temperature").asDouble()).isEqualTo(0.2);
            assertThat(request.body().get("top_p").asDouble()).isEqualTo(0.9);
            assertThat(request.body().get("max_output_tokens").asInt()).isEqualTo(100);
            assertThat(request.organization()).isEqualTo("example-org");
        });
    }

    @Test
    void responsesPresetSendsReasoningVerbosityAndOutputLimitButNoTemperature() {
        var preset = new Preset("main", "gpt-6.1-terra", Api.RESPONSES, "medium", null, null, "low", 500, null, null, null);
        var service = service(Map.of("main", endpoint(EndpointType.OPENAI, "/v1", "secret")), Map.of("large", preset));

        ChatResponse response = service.chatClient("large").prompt("Hello").call().chatResponse();

        assertThat(response.getResult().getOutput().getText()).isEqualTo("OK");
        assertThat(LlmClientService.presetOf(response.getMetadata())).isEqualTo("large");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/v1/responses");
            assertThat(request.body().get("model").asString()).isEqualTo("gpt-6.1-terra");
            assertThat(request.body().get("reasoning").get("effort").asString()).isEqualTo("medium");
            assertThat(request.body().get("text").get("verbosity").asString()).isEqualTo("low");
            assertThat(request.body().get("max_output_tokens").asInt()).isEqualTo(500);
            assertThat(request.body().get("store").asBoolean()).isFalse();
            assertThat(request.body().has("temperature")).isFalse();
        });
    }

    @Test
    void endpointWithoutKeySendsNoAuthorizationHeader() {
        var service = service(Map.of("local", endpoint(EndpointType.OPENAI, "/v1/", null)),
                Map.of("local", new Preset("local", "qwen/qwen3.8-27b", Api.RESPONSES, null, null, null, null, null, null, null, null)));

        service.chatModel("local").call(new Prompt("Hello"));

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.authorization()).isNull();
            assertThat(request.azureKey()).isNull();
        });
    }

    @Test
    void microsoftFoundryEndpointSendsTheAzureKeyHeader() {
        var service = service(Map.of("azure", endpoint(EndpointType.MICROSOFT_FOUNDRY, "/openai/v1/", "azure-secret")),
                Map.of("large", new Preset("azure", "my-deployment", Api.RESPONSES, null, null, null, null, null, null, null, null)));

        service.chatModel("large").call(new Prompt("Hello"));

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.azureKey()).isEqualTo("azure-secret");
            assertThat(request.authorization()).isNull();
            assertThat(request.path()).isEqualTo("/openai/v1/responses");
            assertThat(request.body().get("model").asString()).isEqualTo("my-deployment");
        });
    }

    @Test
    void presetRetriesOverrideTheEndpointAndFinalErrorsRetainStatusHeadersAndBody() {
        var endpoint = endpoint(EndpointType.OPENAI, "/v1", "secret");
        var retrying = new Preset("main", "m", Api.RESPONSES, null, null, null, null, null, null, Duration.ofSeconds(20), 2);
        var normal = new Preset("main", "m", Api.RESPONSES, null, null, null, null, null, null, null, null);
        var service = service(Map.of("main", endpoint), Map.of("retrying", retrying, "normal", normal));

        statusCodes.addAll(List.of(503, 429, 200, 429));
        assertThat(service.chatModel("retrying").call(new Prompt("Hello")).getResult().getOutput().getText()).isEqualTo("OK");
        assertThatThrownBy(() -> service.chatModel("normal").call(new Prompt("Hello"))).isInstanceOfSatisfying(RestClientResponseException.class, ex -> {
            assertThat(ex.getStatusCode().value()).isEqualTo(429);
            assertThat(ex.getResponseHeaders().getFirst("Retry-After")).isEqualTo("0");
            assertThat(ex.getResponseBodyAsString()).contains("provider unavailable");
        });
        assertThat(requests).hasSize(4);
        assertThat(service.effectiveMaxRetries("retrying")).isEqualTo(2);
        assertThat(service.effectiveMaxRetries("normal")).isZero();
    }

    @Test
    void longerProviderCooldownIsPropagatedWithoutAnEarlyRetry() {
        var preset = new Preset("main", "m", Api.RESPONSES, null, null, null, null, null, null, null, 2);
        var service = service(Map.of("main", endpoint(EndpointType.OPENAI, "/v1", null)), Map.of("chat", preset));
        statusCodes.add(429);
        retryAfter = "3600";

        assertThatThrownBy(() -> service.chatModel("chat").call(new Prompt("Hello"))).isInstanceOfSatisfying(RestClientResponseException.class,
                ex -> assertThat(ex.getResponseHeaders().getFirst("Retry-After")).isEqualTo("3600"));
        assertThat(requests).hasSize(1);
    }

    @Test
    void presetTimeoutLimitsTheActualRequest() {
        var preset = new Preset("main", "m", Api.RESPONSES, null, null, null, null, null, null, Duration.ofMillis(200), 0);
        var service = service(Map.of("main", endpoint(EndpointType.OPENAI, "/v1", null)), Map.of("short", preset));
        delayAnswer = true;

        assertThatThrownBy(() -> service.chatModel("short").call(new Prompt("Hello"))).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void embeddingsDeadlineIncludesAnActiveResponseBody() {
        var preset = new Preset("main", "e", Api.EMBEDDINGS, null, null, null, null, null, null, null, null);
        var service = service(Map.of("main", endpoint(EndpointType.OPENAI, "/v1", null)), Map.of("embed", preset));
        // Establish the connection before exercising the response deadline.
        service.embed("embed", List.of("warmup"), Duration.ofSeconds(2));
        trickleAnswer = true;

        assertThatThrownBy(() -> service.embed("embed", List.of("slow"), Duration.ofMillis(200))).isInstanceOf(ResourceAccessException.class);
        assertThat(requests).hasSize(2);
    }

    @Test
    void legacyFoundryUsesTheDeploymentPathAndVersion() {
        var endpoint = new Endpoint(EndpointType.MICROSOFT_FOUNDRY, baseUrl(""), "azure-key", Duration.ofSeconds(3), 0, null, "2025-03-01-preview", null, null);
        var preset = new Preset("main", "deployment", Api.RESPONSES, null, null, null, null, null, null, null, null);
        var service = service(Map.of("main", endpoint), Map.of("chat", preset));
        service.chatModel("chat").call(new Prompt("Hello", LlmChatOptions.builder().model("override").build()));

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/openai/deployments/override/responses");
            assertThat(request.query()).isEqualTo("api-version=2025-03-01-preview");
        });
    }

    @Test
    void anIsolatedModelUsesItsOwnLimitsAndKeepsPromptsOutOfTracing() {
        var observed = new ArrayList<String>();
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                observed.add(context.getName() + " " + context.getLowCardinalityKeyValues() + " " + context.getHighCardinalityKeyValues());
            }
        });
        var preset = new Preset("main", "m", Api.RESPONSES, null, null, null, null, null, null, null, null);
        var service = service(Map.of("main", endpoint(EndpointType.OPENAI, "/v1", "secret")), Map.of("welcome", preset), registry);

        service.isolatedChatModel("welcome", Duration.ofSeconds(3), 0).call(new Prompt("private student situation"));
        // the shared connection still records its HTTP request (method, URL, status), but no chat model observation carries the prompt or the answer
        assertThat(observed).noneMatch(entry -> entry.startsWith("gen_ai")).noneMatch(entry -> entry.contains("private student situation"));
        assertThat(observed).anyMatch(entry -> entry.startsWith("okhttp.requests"));

        service.chatModel("welcome").call(new Prompt("normal"));
        assertThat(observed).as("the shared model is observed as before").anyMatch(entry -> entry.startsWith("gen_ai"));
    }

    @Test
    void chatModelsAreCachedPerPresetAndOnlyChatPresetsHaveOne() {
        var endpoint = endpoint(EndpointType.OPENAI, "/v1", "secret");
        var service = service(Map.of("main", endpoint), Map.of("chat", new Preset("main", "m", Api.RESPONSES, null, null, null, null, null, null, null, null), "embed",
                new Preset("main", "e", Api.EMBEDDINGS, null, null, null, null, null, null, null, null)));

        assertThat(service.chatModel("chat")).isSameAs(service.chatModel("chat"));
        assertThatIllegalArgumentException().isThrownBy(() -> service.chatModel("embed")).withMessageContaining("embeddings preset");
        assertThatIllegalArgumentException().isThrownBy(() -> service.chatModel("missing")).withMessageContaining("missing is not configured");
    }

    @Test
    void embeddingsPresetSendsItsModelAndTheInputsAsFloatsWithTheCallersTimeout() {
        var endpoint = endpoint(EndpointType.OPENAI, "/v1", "secret");
        var service = service(Map.of("main", endpoint),
                Map.of("embed", new Preset("main", "Qwen/Qwen3-Embedding-8B", Api.EMBEDDINGS, null, null, null, null, null, null, null, null), "chat",
                        new Preset("main", "m", Api.RESPONSES, null, null, null, null, null, null, null, null)));

        var response = service.embed("embed", List.of("first", "second"), Duration.ofSeconds(2));

        assertThat(response.data()).singleElement().satisfies(embedding -> assertThat(embedding.embedding()).containsExactly(0.5f, -0.5f));
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/v1/embeddings");
            assertThat(request.authorization()).isEqualTo("Bearer secret");
            assertThat(request.customHeader()).isEqualTo("on");
            assertThat(request.body().get("model").asString()).isEqualTo("Qwen/Qwen3-Embedding-8B");
            assertThat(request.body().get("input")).hasSize(2);
            assertThat(request.body().get("encoding_format").asString()).isEqualTo("float");
        });
        assertThatIllegalArgumentException().isThrownBy(() -> service.embed("chat", List.of("text"), Duration.ofSeconds(2))).withMessageContaining("not an embeddings preset");
        assertThatIllegalArgumentException().isThrownBy(() -> service.embed("embed", List.of("text"), Duration.ZERO)).withMessageContaining("timeout");
        assertThatIllegalArgumentException().isThrownBy(() -> service.embed("embed", List.of("text"), Duration.ofSeconds(-1))).withMessageContaining("timeout");
        assertThatIllegalArgumentException().isThrownBy(() -> service.embed("embed", List.of("text"), null)).withMessageContaining("timeout");
    }

    private String baseUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private Endpoint endpoint(EndpointType type, String path, @Nullable String apiKey) {
        return new Endpoint(type, baseUrl(path), apiKey, Duration.ofSeconds(300), 0, "example-org", null, Map.of("X-Test", "on"), List.of());
    }

    private LlmClientService service(Map<String, Endpoint> endpoints, Map<String, Preset> presets) {
        return service(endpoints, presets, ObservationRegistry.NOOP);
    }

    private LlmClientService service(Map<String, Endpoint> endpoints, Map<String, Preset> presets, ObservationRegistry observations) {
        factory = new LlmClientService(new LlmProperties(endpoints, presets, null), new JsonMapper(), observations, new SimpleMeterRegistry());
        return factory;
    }
}
