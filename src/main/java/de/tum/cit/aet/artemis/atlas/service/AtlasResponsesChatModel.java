package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.Tool;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Synchronous Spring AI {@link ChatModel} adapter for the OpenAI Responses API.
 *
 * <p>
 * Spring AI 2.0.1 does not expose Responses as a chat model. Atlas therefore uses this narrow
 * adapter behind a dedicated {@code ChatClient}; Spring AI's native {@code ToolCallingAdvisor}
 * owns tool execution and recursion. Each request is stateless ({@code store=false}) and carries
 * the complete conversation history, including the original ordered Responses output items. The
 * latter is required to replay reasoning items and their encrypted content on the next round.
 */
public final class AtlasResponsesChatModel implements ChatModel {

    /** Metadata key containing the original ordered Responses output items. */
    public static final String RESPONSES_OUTPUT_ITEMS_METADATA_KEY = "atlas.responses.output-items";

    /** Metadata indicating whether every provider response in the replayed conversation supplied usage. */
    public static final String USAGE_COMPLETE_METADATA_KEY = "atlas.responses.usage-complete";

    private final OpenAIClient openAIClient;

    private final JsonMapper objectMapper;

    private final OpenAiChatOptions defaultOptions;

    /**
     * Creates an Atlas Responses adapter.
     *
     * @param openAIClient provider-aware synchronous OpenAI client
     * @param objectMapper JSON parser used for tool schemas
     * @param defaultModel model/deployment used when a request does not override it
     */
    public AtlasResponsesChatModel(OpenAIClient openAIClient, JsonMapper objectMapper, @Nullable String defaultModel) {
        this.openAIClient = openAIClient;
        this.objectMapper = objectMapper;
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        if (StringUtils.hasText(defaultModel)) {
            options.deploymentName(defaultModel);
        }
        this.defaultOptions = options.store(false).build();
    }

    /**
     * Returns the adapter's default OpenAI options.
     *
     * @return default options with stateless Responses storage
     */
    @Override
    public ChatOptions getOptions() {
        return defaultOptions;
    }

    /**
     * Sends one complete prompt to the Responses API and converts its output to Spring AI's
     * response model. Failed or incomplete provider responses carry a failure finish reason
     * without executable tools, retaining usage for accounting.
     *
     * @param prompt ordered system, user, assistant, and tool messages
     * @return converted assistant response
     * @throws IllegalArgumentException if the prompt contains unsupported content or malformed tools
     */
    @Override
    public ChatResponse call(Prompt prompt) {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Atlas Responses dispatch interrupted before request");
        }
        OpenAiChatOptions options = requireOpenAiOptions(prompt.getOptions());
        ResponseCreateParams.Builder request = ResponseCreateParams.builder().inputOfResponse(toInputItems(prompt.getInstructions())).store(false)
                .include(List.of(ResponseIncludable.REASONING_ENCRYPTED_CONTENT));

        String model = resolveModel(options);
        request.model(model);
        applyReasoningAndSampling(request, options);
        applyTools(request, options);

        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Atlas Responses dispatch interrupted before request");
        }
        Response response = openAIClient.responses().create(request.build());
        boolean priorUsageComplete = prompt.getInstructions().stream().noneMatch(message -> Boolean.FALSE.equals(message.getMetadata().get(USAGE_COMPLETE_METADATA_KEY)));
        return toChatResponse(response, priorUsageComplete);
    }

    private OpenAiChatOptions requireOpenAiOptions(@Nullable ChatOptions options) {
        if (options == null) {
            return defaultOptions;
        }
        if (!(options instanceof OpenAiChatOptions openAiOptions)) {
            throw new IllegalArgumentException("Atlas Responses requires OpenAiChatOptions, got " + options.getClass().getName());
        }
        return openAiOptions;
    }

    private String resolveModel(OpenAiChatOptions options) {
        if (StringUtils.hasText(options.getDeploymentName())) {
            return options.getDeploymentName();
        }
        if (StringUtils.hasText(options.getModel())) {
            return options.getModel();
        }
        if (StringUtils.hasText(defaultOptions.getDeploymentName())) {
            return defaultOptions.getDeploymentName();
        }
        throw new IllegalArgumentException("Atlas Responses requires a model or deployment name");
    }

    private static void applyReasoningAndSampling(ResponseCreateParams.Builder request, OpenAiChatOptions options) {
        if (StringUtils.hasText(options.getReasoningEffort())) {
            request.reasoning(Reasoning.builder().effort(ReasoningEffort.of(options.getReasoningEffort())).build());
        }
        else if (options.getTemperature() != null) {
            request.temperature(options.getTemperature());
        }
        if (options.getParallelToolCalls() != null) {
            request.parallelToolCalls(options.getParallelToolCalls());
        }
    }

    private void applyTools(ResponseCreateParams.Builder request, OpenAiChatOptions options) {
        List<ToolCallback> callbacks = options.getToolCallbacks();
        if (callbacks == null) {
            return;
        }
        for (ToolCallback callback : callbacks) {
            if (callback == null) {
                continue;
            }
            request.addTool(Tool.ofFunction(toFunctionTool(callback.getToolDefinition())));
        }
    }

    private FunctionTool toFunctionTool(ToolDefinition definition) {
        Map<String, Object> schema;
        try {
            schema = objectMapper.readValue(definition.inputSchema(), new TypeReference<>() {
            });
        }
        catch (JacksonException ex) {
            throw new IllegalArgumentException("Invalid JSON schema for Atlas tool " + definition.name(), ex);
        }
        Map<String, JsonValue> parameters = new LinkedHashMap<>();
        schema.forEach((key, value) -> parameters.put(key, JsonValue.from(value)));
        FunctionTool.Builder tool = FunctionTool.builder().name(definition.name()).strict(false)
                .parameters(FunctionTool.Parameters.builder().additionalProperties(parameters).build());
        if (StringUtils.hasText(definition.description())) {
            tool.description(definition.description());
        }
        return tool.build();
    }

    private static List<ResponseInputItem> toInputItems(List<Message> messages) {
        List<ResponseInputItem> inputItems = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                inputItems.add(easyMessage(EasyInputMessage.Role.SYSTEM, systemMessage.getText()));
            }
            else if (message instanceof UserMessage userMessage) {
                if (!userMessage.getMedia().isEmpty()) {
                    throw unsupported("user media");
                }
                inputItems.add(easyMessage(EasyInputMessage.Role.USER, userMessage.getText()));
            }
            else if (message instanceof AssistantMessage assistantMessage) {
                inputItems.addAll(toAssistantItems(assistantMessage));
            }
            else if (message instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    inputItems.add(ResponseInputItem.ofFunctionCallOutput(
                            ResponseInputItem.FunctionCallOutput.builder().callId(response.id()).output(response.responseData() == null ? "" : response.responseData()).build()));
                }
            }
            else {
                throw unsupported("message type " + message.getClass().getName());
            }
        }
        return List.copyOf(inputItems);
    }

    private static ResponseInputItem easyMessage(EasyInputMessage.Role role, @Nullable String text) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder().role(role).content(text == null ? "" : text).build());
    }

    private static List<ResponseInputItem> toAssistantItems(AssistantMessage assistantMessage) {
        Object metadata = assistantMessage.getMetadata().get(RESPONSES_OUTPUT_ITEMS_METADATA_KEY);
        if (metadata != null) {
            if (!(metadata instanceof List<?> outputItems)) {
                throw new IllegalArgumentException("Atlas Responses assistant metadata must contain a list of output items");
            }
            List<ResponseInputItem> replay = new ArrayList<>(outputItems.size());
            for (Object outputItem : outputItems) {
                if (!(outputItem instanceof ResponseOutputItem item)) {
                    throw new IllegalArgumentException("Atlas Responses assistant metadata contains an unsupported output item");
                }
                replay.add(toInputItem(item));
            }
            return List.copyOf(replay);
        }
        if (assistantMessage.hasToolCalls()) {
            throw new IllegalArgumentException("Atlas Responses assistant tool calls require preserved Responses output metadata");
        }
        if (!assistantMessage.getMedia().isEmpty()) {
            throw unsupported("assistant media");
        }
        return List.of(easyMessage(EasyInputMessage.Role.ASSISTANT, assistantMessage.getText()));
    }

    private static ResponseInputItem toInputItem(ResponseOutputItem outputItem) {
        if (outputItem.isReasoning()) {
            return ResponseInputItem.ofReasoning(outputItem.asReasoning());
        }
        if (outputItem.isFunctionCall()) {
            return ResponseInputItem.ofFunctionCall(outputItem.asFunctionCall());
        }
        if (outputItem.isMessage()) {
            return ResponseInputItem.ofResponseOutputMessage(outputItem.asMessage());
        }
        throw unsupported("Responses output item kind " + outputItem.getClass().getSimpleName());
    }

    private static ChatResponse toChatResponse(Response response, boolean priorUsageComplete) {
        boolean usageComplete = priorUsageComplete && response.usage().isPresent();
        ChatResponseMetadata.Builder responseMetadata = ChatResponseMetadata.builder().id(response.id()).model(responseModelName(response)).keyValue(USAGE_COMPLETE_METADATA_KEY,
                usageComplete);
        response.usage().ifPresent(usage -> responseMetadata.usage(toUsage(usage)));
        ResponseStatus status = response.status().orElse(null);
        if (response.error().isPresent() || !ResponseStatus.COMPLETED.equals(status)) {
            // Return a terminal, tool-free response so the native advisor retains earlier rounds' usage.
            // The orchestration layer rejects this finish reason after accounting; incomplete tools never run.
            String finish = response.error().isPresent() ? "failed" : status == null ? "missing_status" : status.asString();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(""), ChatGenerationMetadata.builder().finishReason(finish).build())), responseMetadata.build());
        }
        List<ResponseOutputItem> outputItems = List.copyOf(response.output());
        StringBuilder text = new StringBuilder();
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (ResponseOutputItem outputItem : outputItems) {
            if (outputItem.isReasoning()) {
                continue;
            }
            if (outputItem.isFunctionCall()) {
                ResponseFunctionToolCall functionCall = outputItem.asFunctionCall();
                toolCalls.add(new AssistantMessage.ToolCall(functionCall.callId(), "function", functionCall.name(), functionCall.arguments()));
                continue;
            }
            if (outputItem.isMessage()) {
                appendOutputText(text, outputItem.asMessage());
                continue;
            }
            throw unsupported("Responses output item kind " + outputItem.getClass().getSimpleName());
        }

        Map<String, Object> assistantMetadata = Map.of(RESPONSES_OUTPUT_ITEMS_METADATA_KEY, outputItems, USAGE_COMPLETE_METADATA_KEY, usageComplete);
        AssistantMessage assistant = AssistantMessage.builder().content(text.toString()).toolCalls(toolCalls).properties(assistantMetadata).build();

        return new ChatResponse(List.of(new Generation(assistant)), responseMetadata.build());
    }

    private static String responseModelName(Response response) {
        ResponsesModel model = response.model();
        if (model.isString()) {
            return model.asString();
        }
        if (model.isChat()) {
            return model.asChat().asString();
        }
        if (model.isOnly()) {
            return model.asOnly().asString();
        }
        throw unsupported("Responses model " + model.getClass().getSimpleName());
    }

    private static void appendOutputText(StringBuilder text, ResponseOutputMessage message) {
        message.content().forEach(content -> {
            if (!content.isOutputText()) {
                throw unsupported("Responses message content kind " + content.getClass().getSimpleName());
            }
            text.append(content.asOutputText().text());
        });
    }

    private static DefaultUsage toUsage(ResponseUsage usage) {
        return new DefaultUsage(Math.toIntExact(usage.inputTokens()), Math.toIntExact(usage.outputTokens()), Math.toIntExact(usage.totalTokens()), usage);
    }

    private static IllegalArgumentException unsupported(String kind) {
        return new IllegalArgumentException("Unsupported Atlas Responses " + kind);
    }
}
