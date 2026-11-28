package de.tum.cit.aet.artemis.core.service.llm;

import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.functionCall;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.message;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.model;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.reasoning;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.response;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.withUsage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.function.FunctionToolCallback;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.aiworker.api.LlmChatOptions;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/** Exercises the wire contract and Spring AI's tool-calling integration. */
class OpenAiResponsesChatModelTest {

    private final List<Map<String, Object>> requests = new ArrayList<>();

    private OpenAiResponsesChatModel modelRespondingWith(Map<String, Object> reply) {
        return model("default-model", request -> {
            requests.add(request);
            return reply;
        });
    }

    @Test
    void nativeToolCallingAdvisorExecutesCallbackAndReplaysOrderedResponsesItems() {
        var reasoning = new LinkedHashMap<>(reasoning("sealed-first"));
        reasoning.put("provider_extension", List.of(Map.of("unexpected", "preserve")));
        var first = response(List.of(reasoning, functionCall()), "first");
        var second = response(List.of(reasoning("sealed-second"), message("done")), "second");
        var adapter = model("gpt-test", request -> {
            requests.add(request);
            return requests.size() == 1 ? first : second;
        });
        var marker = new AtomicReference<String>();
        var callback = FunctionToolCallback.<Map<String, Object>, String>builder("lookup", (arguments, context) -> {
            marker.set((String) context.getContext().get("marker"));
            return arguments.get("value").toString();
        }).description("Looks up a value.").inputType(Map.class).inputSchema("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}}}").build();
        var client = ChatClient.builder(adapter).defaultAdvisors(ToolCallingAdvisor.builder().build()).build();

        var result = client.prompt().user("find it").toolContext(Map.of("marker", "ctx")).tools(callback).call().chatResponse();

        assertThat(result.getResult().getOutput().getText()).isEqualTo("done");
        assertThat(marker).hasValue("ctx");
        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst()).containsEntry("store", false).containsEntry("include", List.of("reasoning.encrypted_content"));
        assertThat(requests.getFirst().get("tools")).isEqualTo(List.of(Map.of("type", "function", "name", "lookup", "description", "Looks up a value.", "strict", false,
                "parameters", Map.of("type", "object", "properties", Map.of("value", Map.of("type", "string"))))));
        assertThat(requests.getLast().get("input")).isEqualTo(List.of(Map.of("type", "message", "role", "user", "content", "find it"), reasoning, functionCall(),
                Map.of("type", "function_call_output", "call_id", "call-1", "output", "\"x\"")));
    }

    @Test
    void sendsStructuredOutputAndEffortWithoutTemperature() {
        var adapter = modelRespondingWith(response(List.of(message("{}")), "done"));
        var options = LlmChatOptions.builder().reasoningEffort("high").temperature(0.5).topP(0.9).verbosity("low").maxTokens(3000).maxCompletionTokens(2000)
                .outputSchema("{\"type\":\"object\",\"additionalProperties\":false}").build();

        adapter.call(new Prompt("work", options));

        assertThat(requests.getFirst()).containsEntry("model", "default-model").containsEntry("reasoning", Map.of("effort", "high")).containsEntry("max_output_tokens", 2000)
                .containsEntry("top_p", 0.9).doesNotContainKeys("temperature", "tools");
        assertThat(requests.getFirst().get("text")).isEqualTo(Map.of("verbosity", "low", "format",
                Map.of("type", "json_schema", "name", "response", "strict", true, "schema", Map.of("type", "object", "additionalProperties", false))));
    }

    @Test
    void perRequestOutputLimitOverridesThePresetAndRetainsItsSamplingOptions() {
        var defaults = LlmChatOptions.builder().model("preset").maxCompletionTokens(8000).reasoningEffort("none").temperature(0.6).topP(0.9).build();
        var adapter = model(defaults, request -> {
            requests.add(request);
            return response(List.of(message("done")), "limit");
        });
        adapter.call(new Prompt("work", ChatOptions.builder().maxTokens(40).build()));
        var client = ChatClient.create(adapter);
        client.prompt("work").options(ChatOptions.builder().maxTokens(41)).call().content();
        client.prompt("work").options(LlmChatOptions.builder().maxTokens(42)).call().content();
        client.prompt("work").call().content();

        assertThat(requests.get(1)).containsEntry("max_output_tokens", 41);
        assertThat(requests.get(2)).containsEntry("max_output_tokens", 42);
        assertThat(requests.getFirst()).containsEntry("max_output_tokens", 40).containsEntry("reasoning", Map.of("effort", "none")).containsEntry("temperature", 0.6)
                .containsEntry("top_p", 0.9);
        assertThat(requests.getLast()).containsEntry("max_output_tokens", 8000);
    }

    @Test
    void mutatingOptionsKeepsTheOriginalSchemaAndGenerationSettings() {
        var original = LlmChatOptions.builder().model("m").maxCompletionTokens(55).reasoningEffort("medium").verbosity("low").parallelToolCalls(false).outputSchema("{}").build();
        var copy = original.mutate().build();
        var updated = copy.mutate().outputSchema("{\"type\":\"object\"}").build();

        assertThat(copy).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(updated).isNotEqualTo(original);
        assertThat(original.getOutputSchema()).isEqualTo("{}");
        assertThat(updated.getMaxCompletionTokens()).isEqualTo(55);
    }

    @Test
    void missingUsageIsPreservedAsAbsent() {
        var adapter = modelRespondingWith(response(List.of(message("done")), "missing-usage"));
        assertThat(adapter.call(new Prompt("hello")).getMetadata().getUsage()).isInstanceOf(EmptyUsage.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "custom-string", "gpt-5.6-luna", "o3-pro" })
    void preservesResponseModelIdentityAndAcceptsPortableChatOptions(String model) {
        var reply = response(List.of(message("done")), "model-identity");
        reply.put("model", model);
        var adapter = modelRespondingWith(reply);

        var result = adapter.call(new Prompt("hello", ChatOptions.builder().model("portable-model").build()));

        assertThat(result.getMetadata().getModel()).isEqualTo(model);
        assertThat(requests.getFirst()).containsEntry("model", "portable-model");
    }

    @ParameterizedTest
    @ValueSource(strings = { "incomplete", "failed", "queued", "cancelled" })
    void unsuccessfulResponsesPreserveUsageWithoutExecutingTools(String status) {
        var reply = response(List.of(functionCall()), "failed");
        reply.put("status", status);
        withUsage(reply, 10, 2);
        var result = modelRespondingWith(reply).call(new Prompt("work"));
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo(status);
        assertThat(result.hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getUsage().getTotalTokens()).isEqualTo(12);
    }

    @Test
    void outputTokenLimitPreservesTheTruncationSignalAndSuppressesPartialToolCalls() {
        var reply = response(List.of(functionCall()), "truncated");
        reply.put("status", "incomplete");
        reply.put("incomplete_details", Map.of("reason", "max_output_tokens"));
        withUsage(reply, 10, 2);

        var result = modelRespondingWith(reply).call(new Prompt("work"));

        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("length");
        assertThat(result.hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getUsage().getCompletionTokens()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "model", "status", "output" })
    void malformedEnvelopePreservesUsageWithoutExecutingTools(String field) {
        var reply = response(List.of(functionCall()), "malformed");
        reply.put(field, Map.of("malformed", true));
        withUsage(reply, 10, 2);

        var result = modelRespondingWith(reply).call(new Prompt("work"));

        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("unsupported_output");
        assertThat(result.hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getUsage().getTotalTokens()).isEqualTo(12);
    }

    @ParameterizedTest
    @MethodSource("malformedOutputItems")
    void malformedSiblingSuppressesValidToolCallAndPreservesUsage(Map<String, Object> malformed) {
        var reply = response(List.of(functionCall(), malformed), "malformed-output");
        withUsage(reply, 10, 2);

        var result = modelRespondingWith(reply).call(new Prompt("work"));

        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("unsupported_output");
        assertThat(result.hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getUsage().getTotalTokens()).isEqualTo(12);
    }

    private static Stream<Map<String, Object>> malformedOutputItems() {
        return Stream.of(Map.of("type", "function_call", "name", "lookup"), Map.of("type", "function_call", "call_id", "id", "name", 42, "arguments", "{}"),
                Map.of("type", "function_call", "call_id", "id", "name", "lookup", "arguments", false), Map.of("type", "message"),
                Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", 42))),
                Map.of("type", "message", "content", List.of(Map.of("type", "refusal", "refusal", "declined"))), Map.of("type", "unknown"));
    }

    @Test
    void overflowingUsageReturnsUnknownTerminalResponse() {
        var reply = response(List.of(functionCall()), "overflowing-usage");
        withUsage(reply, (long) Integer.MAX_VALUE + 1, 2);
        var result = modelRespondingWith(reply).call(new Prompt("work"));
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("invalid_usage");
        assertThat(result.hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getUsage()).isInstanceOf(EmptyUsage.class);
    }

    @Test
    void cancelledDispatchNeverContactsProvider() {
        var adapter = modelRespondingWith(Map.of());
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> adapter.call(new Prompt("work"))).isInstanceOf(CancellationException.class);
            assertThat(requests).isEmpty();
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsMissingModelBeforeDispatch() {
        var adapter = model("", request -> {
            throw new AssertionError("Unexpected provider call");
        });
        assertThatThrownBy(() -> adapter.call(new Prompt("work"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires a model");
    }

    @ParameterizedTest
    @MethodSource("invalidReplayMessages")
    void rejectsInvalidReplayBeforeDispatch(AssistantMessage message, String error) {
        var adapter = modelRespondingWith(Map.of());
        assertThatThrownBy(() -> adapter.call(new Prompt(List.of(message)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(error);
        assertThat(requests).isEmpty();
    }

    private static Stream<Arguments> invalidReplayMessages() {
        var malformedItems = Stream
                .of("{\"type\":\"message\"}", "{\"type\":\"message\",\"content\":null}", "{\"type\":\"message\",\"content\":[null]}", "{\"type\":\"message\",\"content\":[42]}",
                        "{\"type\":\"message\",\"content\":[{\"type\":\"refusal\",\"text\":\"declined\"}]}", "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\"}]}",
                        "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":42}]}")
                .map(item -> Arguments.of(preservedMessage(List.of(item)), "unsupported output item"));
        return Stream.concat(malformedItems,
                Stream.of(Arguments.of(preservedMessage("broken"), "list of output items"), Arguments.of(preservedMessage(List.of("broken")), "unsupported output item"),
                        Arguments.of(preservedMessage(List.of("{\"type\":\"reasoning\"} {}")), "unsupported output item"),
                        Arguments.of(AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall("call", "function", "lookup", "{}"))).build(),
                                "preserved Responses output metadata")));
    }

    private static AssistantMessage preservedMessage(Object output) {
        return AssistantMessage.builder().content("ignored duplicate").properties(Map.of(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, output)).build();
    }

    @Test
    void replaysPlainAssistantAndPreservedOutputMessageInOrder() {
        var preserved = message("original output");
        var adapter = modelRespondingWith(response(List.of(message("done")), "replay"));
        var metadata = List.of(JsonObjectMapper.get().writeValueAsString(preserved));
        adapter.call(new Prompt(List.of(new AssistantMessage("plain text"), preservedMessage(metadata)),
                LlmChatOptions.builder().model("").temperature(0.4).parallelToolCalls(false).build()));
        assertThat(requests.getFirst()).containsEntry("model", "default-model").containsEntry("temperature", 0.4).containsEntry("parallel_tool_calls", false)
                .doesNotContainKey("reasoning");
        assertThat(requests.getFirst().get("input")).isEqualTo(List.of(Map.of("type", "message", "role", "assistant", "content", "plain text"), preserved));
    }

    @ParameterizedTest
    @EnumSource(value = JsonInclude.Include.class, names = { "NON_EMPTY", "NON_NULL" })
    void sendsRequiredEmptyMessageContentAndToolResults(JsonInclude.Include inclusion) {
        var tool = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "lookup", null), new ToolResponseMessage.ToolResponse("call-2", "lookup", "result"))).build();
        var mapper = JsonObjectMapper.get().rebuild().changeDefaultPropertyInclusion(value -> value.withValueInclusion(inclusion)).build();
        var adapter = model(LlmChatOptions.builder().model("gpt-test").build(), mapper, request -> {
            requests.add(request);
            return response(List.of(message("done")), "tool-response");
        });
        adapter.call(new Prompt(List.of(new AssistantMessage(""), tool)));
        adapter.call(new Prompt(List.of()));
        assertThat(requests.getLast()).containsEntry("input", List.of()).doesNotContainKeys("temperature", "reasoning", "tools");
        assertThat(requests.getFirst().get("input")).isEqualTo(List.of(Map.of("type", "message", "role", "assistant", "content", ""),
                Map.of("type", "function_call_output", "call_id", "call-1", "output", ""), Map.of("type", "function_call_output", "call_id", "call-2", "output", "result")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{", "{} {}", "[]", "null" })
    void malformedToolSchemaNeverContactsProvider(String schema) {
        var callback = FunctionToolCallback.<Map<String, Object>, String>builder("broken", ignored -> "").inputType(Map.class).inputSchema(schema).build();
        var adapter = modelRespondingWith(Map.of());
        assertThatThrownBy(() -> adapter.call(new Prompt("work", LlmChatOptions.builder().toolCallbacks(callback).build()))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON schema for tool broken");
        assertThat(requests).isEmpty();
    }
}
