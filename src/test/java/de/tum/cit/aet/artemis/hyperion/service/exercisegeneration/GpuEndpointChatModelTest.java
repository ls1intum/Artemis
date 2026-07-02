package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit test for the pure, HTTP-free request/response mapping of {@link GpuEndpointChatModel}. The class otherwise runs only under GPU/Docker-gated end-to-end tests, so this locks
 * the wire-format contract — most importantly that parallel tool-call results are NOT collapsed to one message.
 */
class GpuEndpointChatModelTest {

    private final GpuEndpointChatModel model = new GpuEndpointChatModel("http://localhost", "key", "model");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildRequest_parallelToolResponses_mapToOneToolMessageEach() {
        AssistantMessage assistant = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call_a", "function", "f", "{}"), new AssistantMessage.ToolCall("call_b", "function", "g", "{}"))).build();
        // Spring AI groups the results of two parallel tool calls into ONE ToolResponseMessage.
        ToolResponseMessage tools = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call_a", "f", "result-a"), new ToolResponseMessage.ToolResponse("call_b", "g", "result-b"))).build();

        JsonNode body = model.buildRequest(new Prompt(List.of(assistant, tools)));

        List<JsonNode> toolMessages = new ArrayList<>();
        body.get("messages").forEach(node -> {
            if ("tool".equals(node.path("role").asText())) {
                toolMessages.add(node);
            }
        });
        // Each parallel response becomes its own {role:tool, tool_call_id} message; collapsing them to one would break the tool_call_id pairing the endpoint enforces.
        assertThat(toolMessages).hasSize(2);
        assertThat(toolMessages.get(0).path("tool_call_id").asText()).isEqualTo("call_a");
        assertThat(toolMessages.get(0).path("content").asText()).isEqualTo("result-a");
        assertThat(toolMessages.get(1).path("tool_call_id").asText()).isEqualTo("call_b");
        assertThat(toolMessages.get(1).path("content").asText()).isEqualTo("result-b");
        // The configured model and the default token cap are emitted.
        assertThat(body.path("model").asText()).isEqualTo("model");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(2500);
    }

    @Test
    void buildRequest_overriddenMaxTokens_isEmitted() {
        GpuEndpointChatModel customModel = new GpuEndpointChatModel("http://localhost", "key", "m2", 9000);
        JsonNode body = customModel.buildRequest(new Prompt("hi"));
        assertThat(body.path("max_tokens").asInt()).isEqualTo(9000);
        assertThat(body.path("model").asText()).isEqualTo("m2");
    }

    @Test
    void parseResponse_stripsHarmonyTokens_carriesUsage_andMapsNullContentToEmpty() throws Exception {
        JsonNode withUsage = mapper
                .readTree("{\"choices\":[{\"message\":{\"content\":\"answer<|end|>\"}}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3,\"total_tokens\":10}}");
        ChatResponse response = model.parseResponse(withUsage);
        assertThat(response.getResult().getOutput().getText()).isEqualTo("answer");
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(10);

        JsonNode nullContent = mapper.readTree("{\"choices\":[{\"message\":{\"content\":null}}]}");
        assertThat(model.parseResponse(nullContent).getResult().getOutput().getText()).isEmpty();
    }

    @Test
    void parseResponse_extractsToolCallsWithDefaults_andEmptyUsageWhenAbsent() throws Exception {
        JsonNode root = mapper.readTree("""
                {"choices":[{"message":{"content":null,"tool_calls":[
                  {"id":"tc1","function":{"name":"write_file","arguments":"{\\"path\\":\\"A.java\\"}"}},
                  {"function":{}}
                ]}}]}
                """);
        ChatResponse response = model.parseResponse(root);

        List<AssistantMessage.ToolCall> toolCalls = response.getResult().getOutput().getToolCalls();
        assertThat(toolCalls).hasSize(2);
        assertThat(toolCalls.get(0).id()).isEqualTo("tc1");
        assertThat(toolCalls.get(0).name()).isEqualTo("write_file");
        assertThat(toolCalls.get(0).arguments()).contains("A.java");
        // A tool call missing id/arguments falls back to a generated non-empty id and "{}" arguments.
        assertThat(toolCalls.get(1).id()).isNotEmpty();
        assertThat(toolCalls.get(1).arguments()).isEqualTo("{}");
        // No usage object in the response -> empty usage (zero tokens), not the parsed-metadata path.
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isZero();
    }
}
