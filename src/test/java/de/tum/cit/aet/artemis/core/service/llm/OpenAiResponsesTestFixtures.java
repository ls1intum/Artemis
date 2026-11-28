package de.tum.cit.aet.artemis.core.service.llm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.LlmChatOptions;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import io.micrometer.observation.ObservationRegistry;

/** Exercises the Responses adapter with the JSON the provider would receive and return. */
public final class OpenAiResponsesTestFixtures {

    private OpenAiResponsesTestFixtures() {
    }

    public static OpenAiResponsesChatModel model(String model, Function<Map<String, Object>, Map<String, Object>> provider) {
        return model(LlmChatOptions.builder().model(model).build(), provider);
    }

    public static OpenAiResponsesChatModel model(LlmChatOptions defaults, Function<Map<String, Object>, Map<String, Object>> provider) {
        return model(defaults, JsonObjectMapper.get(), provider);
    }

    static OpenAiResponsesChatModel model(LlmChatOptions defaults, JsonMapper configured, Function<Map<String, Object>, Map<String, Object>> provider) {
        var mapper = LlmHttpClient.wireMapper(configured);
        var client = mock(LlmHttpClient.class);
        when(client.post(eq("responses"), any(), any())).thenAnswer(call -> {
            Map<String, Object> request = mapper.readValue(mapper.writeValueAsBytes(call.getArgument(1, Record.class)), new TypeReference<>() {
            });
            // The provider encodes its own response independently of the application's null/empty inclusion settings.
            Class<?> responseType = call.getArgument(2);
            return mapper.readValue(JsonObjectMapper.get().writeValueAsBytes(provider.apply(request)), responseType);
        });
        return new OpenAiResponsesChatModel(client, mapper, defaults, ObservationRegistry.NOOP);
    }

    public static void withUsage(Map<String, Object> response, long input, long output) {
        response.put("usage", Map.of("input_tokens", input, "output_tokens", output, "total_tokens", input + output));
    }

    public static Map<String, Object> response(List<Map<String, Object>> output, String id) {
        return new LinkedHashMap<>(Map.of("id", id, "model", "gpt-test", "output", output, "status", "completed"));
    }

    public static Map<String, Object> functionCall() {
        return Map.of("id", "fc-1", "type", "function_call", "call_id", "call-1", "name", "lookup", "arguments", "{\"value\":\"x\"}", "status", "completed");
    }

    public static Map<String, Object> reasoning(String encryptedContent) {
        return Map.of("id", "rs-1", "type", "reasoning", "summary", List.of(), "encrypted_content", encryptedContent, "status", "completed");
    }

    public static Map<String, Object> message(String text) {
        return Map.of("id", "msg-1", "type", "message", "role", "assistant", "status", "completed", "content",
                List.of(Map.of("type", "output_text", "text", text, "annotations", List.of())));
    }
}
