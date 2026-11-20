package de.tum.cit.aet.artemis.core.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Api;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.EndpointType;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Preset;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * Sends real SDK requests to an interceptor, so the tests see exactly what reaches the provider for each preset.
 */
class LlmClientServiceTest {

    private static final String CHAT_COMPLETION = """
            {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"served-model",
             "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":3,"completion_tokens":1,"total_tokens":4}}
            """;

    private static final String RESPONSE = """
            {"id":"resp-test","object":"response","created_at":1,"model":"served-model","status":"completed","error":null,"incomplete_details":null,
             "instructions":null,"metadata":{},"parallel_tool_calls":true,"temperature":null,"tool_choice":"auto","tools":[],"top_p":null,
             "output":[{"type":"message","id":"msg-1","role":"assistant","status":"completed","content":[{"type":"output_text","text":"OK","annotations":[]}]}],
             "usage":{"input_tokens":3,"input_tokens_details":{"cached_tokens":0},"output_tokens":1,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":4}}
            """;

    private final List<Captured> requests = new ArrayList<>();

    private record Captured(String path, @Nullable String authorization, @Nullable String azureKey, @Nullable String customHeader, JsonNode body, Duration callTimeout) {
    }

    @Test
    void chatCompletionsPresetSendsExactlyItsOptionsAndTagsTheResponse() {
        var preset = new Preset("main", "gpt-6-luna", Api.CHAT_COMPLETIONS, "none", 0.2, 0.9, null, null, 100, null, null);
        var service = service(Map.of("main", endpoint(EndpointType.OPENAI, "https://llm.example.org/v1", "secret")), Map.of("fast", preset));

        ChatResponse response = service.chatModel("fast").call(new Prompt("Hello"));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("OK");
        assertThat(LlmClientService.presetOf(response.getMetadata())).isEqualTo("fast");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.authorization()).isEqualTo("Bearer secret");
            assertThat(request.customHeader()).isEqualTo("on");
            assertThat(request.body().get("model").asString()).isEqualTo("gpt-6-luna");
            assertThat(request.body().get("reasoning_effort").asString()).isEqualTo("none");
            assertThat(request.body().get("temperature").asDouble()).isEqualTo(0.2);
            assertThat(request.body().get("top_p").asDouble()).isEqualTo(0.9);
            assertThat(request.body().get("max_completion_tokens").asInt()).isEqualTo(100);
            assertThat(request.callTimeout()).isEqualTo(Duration.ofSeconds(300));
        });
    }

    @Test
    void responsesPresetSendsReasoningVerbosityAndOutputLimitButNoTemperature() {
        var preset = new Preset("main", "gpt-6.1-terra", Api.RESPONSES, "medium", null, null, "low", 500, null, null, null);
        var service = service(Map.of("main", endpoint(EndpointType.OPENAI, "https://llm.example.org/v1", "secret")), Map.of("large", preset));

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
        var service = service(Map.of("local", endpoint(EndpointType.OPENAI, "http://localhost:11434/v1", null)),
                Map.of("local", new Preset("local", "gpt-oss:20b", Api.CHAT_COMPLETIONS, null, null, null, null, null, null, null, null)));

        service.chatModel("local").call(new Prompt("Hello"));

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.authorization()).isNull();
            assertThat(request.azureKey()).isNull();
        });
    }

    @Test
    void microsoftFoundryEndpointSendsTheAzureKeyHeader() {
        var service = service(Map.of("azure", endpoint(EndpointType.MICROSOFT_FOUNDRY, "https://resource.openai.azure.com/openai/v1", "azure-secret")),
                Map.of("large", new Preset("azure", "my-deployment", Api.CHAT_COMPLETIONS, null, null, null, null, null, null, null, null)));

        service.chatModel("large").call(new Prompt("Hello"));

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.azureKey()).isEqualTo("azure-secret");
            assertThat(request.authorization()).isNull();
            assertThat(request.path()).isEqualTo("/openai/v1/chat/completions");
            assertThat(request.body().get("model").asString()).isEqualTo("my-deployment");
        });
    }

    @Test
    void presetTimeoutAndRetriesOverrideTheEndpointWhileOtherPresetsKeepIt() {
        var endpoint = endpoint(EndpointType.OPENAI, "https://llm.example.org/v1", "secret");
        var slow = new Preset("main", "m", Api.CHAT_COMPLETIONS, null, null, null, null, null, null, Duration.ofMinutes(20), 2);
        var normal = new Preset("main", "m", Api.CHAT_COMPLETIONS, null, null, null, null, null, null, null, null);
        var service = service(Map.of("main", endpoint), Map.of("slow", slow, "normal", normal));

        service.chatModel("slow").call(new Prompt("Hello"));
        service.chatModel("normal").call(new Prompt("Hello"));

        assertThat(requests).extracting(Captured::callTimeout).containsExactly(Duration.ofMinutes(20), Duration.ofSeconds(300));
        assertThat(service.effectiveTimeout("slow")).isEqualTo(Duration.ofMinutes(20));
        assertThat(service.effectiveMaxRetries("slow")).isEqualTo(2);
        assertThat(service.effectiveMaxRetries("normal")).isZero();
    }

    @Test
    void chatModelsAreCachedPerPresetAndOnlyChatPresetsHaveOne() {
        var endpoint = endpoint(EndpointType.OPENAI, "https://llm.example.org/v1", "secret");
        var service = service(Map.of("main", endpoint), Map.of("chat", new Preset("main", "m", Api.RESPONSES, null, null, null, null, null, null, null, null), "embed",
                new Preset("main", "e", Api.EMBEDDINGS, null, null, null, null, null, null, null, null)));

        assertThat(service.chatModel("chat")).isSameAs(service.chatModel("chat"));
        assertThatIllegalArgumentException().isThrownBy(() -> service.chatModel("embed")).withMessageContaining("embeddings preset");
        assertThatIllegalArgumentException().isThrownBy(() -> service.chatModel("missing")).withMessageContaining("missing is not configured");
    }

    private static Endpoint endpoint(EndpointType type, String baseUrl, @Nullable String apiKey) {
        return new Endpoint(type, baseUrl, apiKey, Duration.ofSeconds(300), 0, null, null, Map.of("X-Test", "on"), List.of());
    }

    private LlmClientService service(Map<String, Endpoint> endpoints, Map<String, Preset> presets) {
        OpenAiHttpClientBuilderCustomizer capture = builder -> builder.interceptor(chain -> {
            var request = chain.request();
            var buffer = new Buffer();
            if (request.body() != null) {
                request.body().writeTo(buffer);
            }
            JsonNode body = new JsonMapper().readTree(buffer.readUtf8());
            requests.add(new Captured(request.url().encodedPath(), request.header("Authorization"), request.header("api-key"), request.header("X-Test"), body,
                    Duration.ofNanos(chain.call().timeout().timeoutNanos())));
            String json = request.url().encodedPath().endsWith("/responses") ? RESPONSE : CHAT_COMPLETION;
            return respond(request, json);
        });
        return new LlmClientService(new LlmProperties(endpoints, presets, null), new JsonMapper(), ObservationRegistry.NOOP, new SimpleMeterRegistry(), List.of(capture));
    }

    private static Response respond(okhttp3.Request request, String json) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(json, MediaType.get("application/json")))
                .build();
    }
}
