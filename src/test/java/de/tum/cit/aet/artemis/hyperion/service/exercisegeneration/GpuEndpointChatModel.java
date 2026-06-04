package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A real {@link ChatModel} that delegates to the GPU OpenWebUI endpoint (OpenAI-compatible tool-calling), used by the end-to-end Hyperion generation test so the production
 * {@code AgentLoopRunner} + {@code ToolCallingManager} drive a real LLM. It maps a Spring AI {@link Prompt} (system/user/assistant/tool messages + tool definitions) into an
 * OpenAI chat-completions request and maps the response (assistant text + tool calls) back into a Spring AI {@link ChatResponse}.
 */
public class GpuEndpointChatModel implements ChatModel {

    private final String baseUrl;

    private final String apiKey;

    private final String model;

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public GpuEndpointChatModel(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            ObjectNode body = buildRequest(prompt);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat/completions")).timeout(Duration.ofSeconds(120)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("GPU endpoint returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return parseResponse(mapper.readTree(response.body()));
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException("GPU endpoint call failed: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildRequest(Prompt prompt) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 2500);
        ArrayNode messages = body.putArray("messages");
        for (Message message : prompt.getInstructions()) {
            messages.add(toOpenAiMessage(message));
        }
        if (prompt.getOptions() instanceof ToolCallingChatOptions options && options.getToolCallbacks() != null && !options.getToolCallbacks().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolCallback callback : options.getToolCallbacks()) {
                tools.add(toToolSchema(callback));
            }
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private ObjectNode toOpenAiMessage(Message message) {
        ObjectNode node = mapper.createObjectNode();
        MessageType type = message.getMessageType();
        if (type == MessageType.SYSTEM) {
            node.put("role", "system");
            node.put("content", message.getText());
        }
        else if (type == MessageType.USER) {
            node.put("role", "user");
            node.put("content", message.getText());
        }
        else if (type == MessageType.ASSISTANT) {
            node.put("role", "assistant");
            AssistantMessage assistant = (AssistantMessage) message;
            node.put("content", assistant.getText() == null ? "" : assistant.getText());
            if (assistant.getToolCalls() != null && !assistant.getToolCalls().isEmpty()) {
                ArrayNode toolCalls = node.putArray("tool_calls");
                for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                    ObjectNode tc = toolCalls.addObject();
                    tc.put("id", call.id());
                    tc.put("type", "function");
                    ObjectNode fn = tc.putObject("function");
                    fn.put("name", call.name());
                    fn.put("arguments", call.arguments());
                }
            }
        }
        else if (type == MessageType.TOOL) {
            // Spring AI groups tool responses; the OpenAI wire format needs one message per tool result.
            ToolResponseMessage toolMessage = (ToolResponseMessage) message;
            ToolResponseMessage.ToolResponse first = toolMessage.getResponses().getFirst();
            node.put("role", "tool");
            node.put("tool_call_id", first.id());
            node.put("content", first.responseData());
        }
        return node;
    }

    private ObjectNode toToolSchema(ToolCallback callback) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", callback.getToolDefinition().name());
        function.put("description", callback.getToolDefinition().description());
        try {
            function.set("parameters", mapper.readTree(callback.getToolDefinition().inputSchema()));
        }
        catch (Exception e) {
            function.putObject("parameters").put("type", "object");
        }
        return tool;
    }

    private ChatResponse parseResponse(JsonNode root) {
        JsonNode message = root.path("choices").path(0).path("message");
        String content = sanitizeHarmonyTokens(message.path("content").isNull() ? "" : message.path("content").asText(""));
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                String id = tc.path("id").asText(UUID.randomUUID().toString());
                String name = tc.path("function").path("name").asText();
                String arguments = tc.path("function").path("arguments").asText("{}");
                toolCalls.add(new AssistantMessage.ToolCall(id, "function", name, arguments));
            }
        }
        AssistantMessage assistantMessage = AssistantMessage.builder().content(content).toolCalls(toolCalls).build();
        // Carry the provider's real token usage through so the production AgentLoopRunner drives its window-aware compaction off the authoritative prompt-token count (exactly as
        // it does against the real OpenAI starter), rather than the conservative char-based fallback.
        JsonNode usage = root.path("usage");
        if (usage.isObject()) {
            DefaultUsage parsed = new DefaultUsage(usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0), usage.path("total_tokens").asInt(0));
            return new ChatResponse(List.of(new Generation(assistantMessage)), ChatResponseMetadata.builder().model(model).usage(parsed).build());
        }
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    /**
     * Strips gpt-oss "harmony" control tokens (e.g. {@code <|channel|>commentary}, {@code <|message|>}, {@code <|start|>}, {@code <|end|>}) that the deployment occasionally leaks
     * into the assistant {@code content}. If such a token is stored and sent back verbatim in the next request, the server's harmony chat template re-parses it as structure — most
     * visibly producing an {@code "Unknown role: assistant<|channel|>commentary"} HTTP 400 that aborts a long, otherwise-healthy run. Removing the control tokens (the visible text
     * around them is harmless) keeps the conversation replayable.
     *
     * @param content the assistant content returned by the endpoint
     * @return the content with any harmony control tokens removed
     */
    private static String sanitizeHarmonyTokens(String content) {
        if (content == null || content.indexOf("<|") < 0) {
            return content;
        }
        return content.replaceAll("<\\|[^|>]*\\|>", "");
    }
}
