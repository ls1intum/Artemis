package de.tum.cit.aet.artemis.core.service.llm;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.ai.chat.metadata.FinishReasonCategory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonValue;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.LlmChatOptions;
import de.tum.cit.aet.artemis.aiworker.api.ResponsesOutputRepair;
import de.tum.cit.aet.artemis.core.config.RawJsonStringDeserializer;
import de.tum.cit.aet.artemis.core.config.StrictIntegerDeserializer;
import de.tum.cit.aet.artemis.core.config.StrictStringDeserializer;
import io.micrometer.observation.ObservationRegistry;

/**
 * Synchronous, stateless Spring AI adapter for the Responses API. Spring AI's ToolCallingAdvisor executes tools.
 * The ordered, original provider output travels with each assistant message so subsequent calls can replay encrypted reasoning.
 */
public final class OpenAiResponsesChatModel implements ChatModel {

    /** Assistant metadata containing the original ordered Responses output items, each encoded as JSON. */
    public static final String RESPONSES_OUTPUT_ITEMS_METADATA_KEY = ResponsesOutputRepair.OUTPUT_ITEMS_METADATA_KEY;

    private static final String MESSAGE = "message";

    private static final String UNSUPPORTED_OUTPUT = "unsupported_output";

    private static final DefaultChatModelObservationConvention OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

    private final LlmHttpClient client;

    private final JsonMapper mapper;

    private final LlmChatOptions defaultOptions;

    private final ObservationRegistry observations;

    OpenAiResponsesChatModel(LlmHttpClient client, JsonMapper mapper, LlmChatOptions defaultOptions, ObservationRegistry observations) {
        this.client = client;
        this.mapper = mapper;
        this.defaultOptions = defaultOptions;
        this.observations = observations;
    }

    @Override
    public ChatOptions getOptions() {
        return defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        checkInterrupted();
        var options = options(prompt.getOptions());
        var request = request(prompt.getInstructions(), options);
        checkInterrupted();
        var context = ChatModelObservationContext.builder().prompt(new Prompt(prompt.getInstructions(), options)).provider("openai").build();
        return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(null, OBSERVATION_CONVENTION, () -> context, observations).observe(() -> {
            var response = toChatResponse(client.post("responses", request, Response.class), request.model());
            context.setResponse(response);
            return response;
        });
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Responses dispatch interrupted before request");
        }
    }

    private LlmChatOptions options(@Nullable ChatOptions options) {
        if (options == null) {
            return defaultOptions;
        }
        if (options instanceof LlmChatOptions llm) {
            return llm;
        }
        return defaultOptions.mutate().combineWith(options.mutate()).build();
    }

    private Request request(List<Message> messages, LlmChatOptions options) {
        String model = StringUtils.hasText(options.getModel()) ? options.getModel() : defaultOptions.getModel();
        if (!StringUtils.hasText(model)) {
            throw new IllegalArgumentException("OpenAI Responses requires a model or deployment name");
        }
        String effort = options.getReasoningEffort();
        var reasoning = StringUtils.hasText(effort) ? new Reasoning(effort) : null;
        Double temperature = reasoning == null || "none".equals(effort) ? options.getTemperature() : null;
        Integer limit = options.getMaxCompletionTokens() != null ? options.getMaxCompletionTokens() : options.getMaxTokens();
        String verbosity = StringUtils.hasText(options.getVerbosity()) ? options.getVerbosity() : null;
        Format format = null;
        if (StringUtils.hasText(options.getOutputSchema())) {
            format = new Format("json_schema", "response", schema(options.getOutputSchema(), "structured output"), true);
        }
        var text = format != null || verbosity != null ? new Text(verbosity, format) : null;
        List<FunctionTool> tools = new ArrayList<>();
        for (ToolCallback callback : Objects.requireNonNullElse(options.getToolCallbacks(), List.<ToolCallback>of())) {
            if (callback == null) {
                continue;
            }
            var definition = callback.getToolDefinition();
            String description = StringUtils.hasText(definition.description()) ? definition.description() : null;
            tools.add(new FunctionTool("function", definition.name(), description, schema(definition.inputSchema(), "tool " + definition.name()), false));
        }
        return new Request(model, input(messages), false, List.of("reasoning.encrypted_content"), reasoning, temperature, options.getTopP(), text, limit,
                options.getParallelToolCalls(), tools.isEmpty() ? null : tools);
    }

    // Schemas belong to the callback or converter. Validate that they are single JSON objects before embedding them verbatim.
    private String schema(String json, String source) {
        try {
            if (mapper.readerFor(Schema.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json) != null) {
                return json;
            }
        }
        catch (JacksonException ex) {
            throw new IllegalArgumentException("Invalid JSON schema for " + source, ex);
        }
        throw new IllegalArgumentException("Invalid JSON schema for " + source);
    }

    private List<InputItem> input(List<Message> messages) {
        List<InputItem> items = new ArrayList<>();
        for (Message message : messages) {
            switch (message) {
                case SystemMessage system -> items.add(new MessageInput("system", system.getText()));
                case UserMessage user -> {
                    if (!user.getMedia().isEmpty()) {
                        throw unsupported("user media");
                    }
                    items.add(new MessageInput("user", user.getText()));
                }
                case AssistantMessage assistant -> addAssistant(items, assistant);
                case ToolResponseMessage tool ->
                    tool.getResponses().forEach(result -> items.add(new ToolOutput("function_call_output", result.id(), Objects.requireNonNullElse(result.responseData(), ""))));
                default -> throw unsupported("message type " + message.getClass().getName());
            }
        }
        return items;
    }

    private void addAssistant(List<InputItem> items, AssistantMessage assistant) {
        Object preserved = assistant.getMetadata().get(RESPONSES_OUTPUT_ITEMS_METADATA_KEY);
        if (preserved != null) {
            if (!(preserved instanceof List<?> output)) {
                throw new IllegalArgumentException("OpenAI Responses assistant metadata must contain a list of output items");
            }
            for (Object item : output) {
                if (!(item instanceof String json)) {
                    throw unsupported("assistant metadata contains an unsupported output item");
                }
                // Metadata may have passed through chat memory. Only replay a single valid, supported output item.
                outputItem(json);
                items.add(new Replay(json));
            }
        }
        else if (assistant.hasToolCalls()) {
            throw new IllegalArgumentException("OpenAI Responses assistant tool calls require preserved Responses output metadata");
        }
        else if (!assistant.getMedia().isEmpty()) {
            throw unsupported("assistant media");
        }
        else {
            items.add(new MessageInput("assistant", assistant.getText()));
        }
    }

    private OutputItem outputItem(String json) {
        try {
            var kind = readItem(json, ItemType.class);
            if (kind == null || kind.type() == null) {
                throw unsupported("output item");
            }
            return switch (kind.type()) {
                case "reasoning" -> ReasoningOutput.INSTANCE;
                case MESSAGE -> readItem(json, MessageOutput.class);
                case "function_call" -> readItem(json, FunctionCall.class);
                default -> throw unsupported("output item");
            };
        }
        catch (JacksonException ex) {
            throw new IllegalArgumentException("OpenAI Responses assistant metadata contains an unsupported output item", ex);
        }
    }

    private <T> T readItem(String json, Class<T> type) {
        return mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
    }

    private ChatResponse toChatResponse(Response response, String requestedModel) {
        var metadata = ChatResponseMetadata.builder().model(requestedModel);
        if (response.usage() != null) {
            try {
                metadata.usage(mapper.readValue(response.usage(), Usage.class).toUsage());
            }
            catch (JacksonException | IllegalArgumentException _) {
                return terminalResponse("invalid_usage", metadata);
            }
        }
        if (!(response.id() instanceof String id) || !(response.model() instanceof String model)) {
            return terminalResponse(UNSUPPORTED_OUTPUT, metadata);
        }
        metadata.id(id).model(model);
        if (response.error() != null) {
            return terminalResponse("failed", metadata);
        }
        if (!(response.status() instanceof String status)) {
            return terminalResponse(response.status() == null ? "missing_status" : UNSUPPORTED_OUTPUT, metadata);
        }
        if (!"completed".equals(status)) {
            boolean truncated = "incomplete".equals(status) && response.incompleteDetails() instanceof Map<?, ?> details && "max_output_tokens".equals(details.get("reason"));
            return terminalResponse(truncated ? "length" : status, metadata);
        }

        return completedResponse(response.output(), metadata);
    }

    private ChatResponse completedResponse(List<String> output, ChatResponseMetadata.Builder metadata) {
        try {
            if (output == null) {
                throw unsupported("missing output");
            }
            StringBuilder text = new StringBuilder();
            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
            for (String json : output) {
                if (json == null) {
                    throw unsupported("null output item");
                }
                switch (outputItem(json)) {
                    case FunctionCall call -> calls.add(call.toToolCall());
                    case MessageOutput message -> message.appendText(text);
                    case ReasoningOutput _ -> { // Reasoning items are replayed unchanged.
                    }
                }
            }
            var assistant = AssistantMessage.builder().content(text.toString()).toolCalls(calls).properties(Map.of(RESPONSES_OUTPUT_ITEMS_METADATA_KEY, List.copyOf(output)))
                    .build();
            return new ChatResponse(List.of(new Generation(assistant)), metadata.build());
        }
        catch (IllegalArgumentException _) {
            // Keep billable usage, but never execute a tool returned alongside malformed or unsupported output.
            return terminalResponse(UNSUPPORTED_OUTPUT, metadata);
        }
    }

    private static ChatResponse terminalResponse(String reason, ChatResponseMetadata.Builder metadata) {
        var category = "length".equals(reason) ? FinishReasonCategory.TRUNCATED : FinishReasonCategory.OTHER;
        var generationMetadata = ChatGenerationMetadata.builder().finishReason(reason).finishReasonCategory(category).build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(""), generationMetadata)), metadata.build());
    }

    private static IllegalArgumentException unsupported(String kind) {
        return new IllegalArgumentException("Unsupported OpenAI Responses " + kind);
    }

    private record Request(String model, List<InputItem> input, boolean store, List<String> include, Reasoning reasoning, Double temperature, @JsonProperty("top_p") Double topP,
            Text text, @JsonProperty("max_output_tokens") Integer maxOutputTokens, @JsonProperty("parallel_tool_calls") Boolean parallelToolCalls, List<FunctionTool> tools) {
    }

    private interface InputItem {
    }

    private record MessageInput(String type, String role, String content) implements InputItem {

        MessageInput(String role, String content) {
            this(MESSAGE, role, Objects.requireNonNullElse(content, ""));
        }
    }

    private record ToolOutput(String type, @JsonProperty("call_id") String callId, String output) implements InputItem {
    }

    private record Replay(@JsonValue @JsonRawValue String json) implements InputItem {
    }

    private record Reasoning(String effort) {
    }

    private record Text(String verbosity, Format format) {
    }

    private record Format(String type, String name, @JsonRawValue String schema, boolean strict) {
    }

    private record FunctionTool(String type, String name, String description, @JsonRawValue String parameters, boolean strict) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Schema() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Response(Object id, Object model, Object status, Object error, @JsonProperty("incomplete_details") Object incompleteDetails,
            @JsonDeserialize(using = OutputItemsDeserializer.class) List<String> output, @JsonDeserialize(using = RawJsonStringDeserializer.class) String usage) {
    }

    // A malformed output still carries billable usage. Preserve each item as JSON for exact replay, including provider extensions.
    private static final class OutputItemsDeserializer extends ValueDeserializer<List<String>> {

        @Override
        public @Nullable List<String> deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.isExpectedStartArrayToken()) {
                parser.skipChildren();
                return null;
            }
            List<String> items = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                var item = new StringWriter();
                try (var generator = context.tokenStreamFactory().createGenerator(ObjectWriteContext.empty(), item)) {
                    generator.copyCurrentStructureExact(parser);
                }
                items.add(item.toString());
            }
            return items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemType(String type) {
    }

    private sealed interface OutputItem {
    }

    private enum ReasoningOutput implements OutputItem {
        INSTANCE
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FunctionCall(@JsonProperty("call_id") @JsonDeserialize(using = StrictStringDeserializer.class) String callId,
            @JsonDeserialize(using = StrictStringDeserializer.class) String name, @JsonDeserialize(using = StrictStringDeserializer.class) String arguments) implements OutputItem {

        FunctionCall {
            if (callId == null || name == null || arguments == null) {
                throw unsupported("incomplete function call");
            }
        }

        AssistantMessage.ToolCall toToolCall() {
            return new AssistantMessage.ToolCall(callId, "function", name, arguments);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessageOutput(List<Content> content) implements OutputItem {

        MessageOutput {
            if (content == null) {
                throw unsupported("missing message content");
            }
            for (Content part : content) {
                if (part == null || !"output_text".equals(part.type()) || part.text() == null) {
                    throw unsupported("message content");
                }
            }
        }

        void appendText(StringBuilder text) {
            content.forEach(part -> text.append(part.text()));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Content(String type, @JsonDeserialize(using = StrictStringDeserializer.class) String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(@JsonProperty("input_tokens") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer inputTokens,
            @JsonProperty("output_tokens") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer outputTokens,
            @JsonProperty("total_tokens") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer totalTokens, @JsonProperty("input_tokens_details") Object inputDetails,
            @JsonProperty("output_tokens_details") Object outputDetails, Object cost, @JsonProperty("cost_currency") Object currency) {

        DefaultUsage toUsage() {
            if (inputTokens == null || outputTokens == null || totalTokens == null) {
                throw new IllegalArgumentException("Missing Responses usage counter");
            }
            var nativeUsage = new LinkedHashMap<String, Object>();
            nativeUsage.put("prompt_tokens", inputTokens);
            nativeUsage.put("completion_tokens", outputTokens);
            nativeUsage.put("total_tokens", totalTokens);
            if (inputDetails != null) {
                nativeUsage.put("input_tokens_details", inputDetails);
            }
            if (outputDetails != null) {
                nativeUsage.put("output_tokens_details", outputDetails);
            }
            if (cost != null) {
                nativeUsage.put("cost", cost);
            }
            if (currency != null) {
                nativeUsage.put("cost_currency", currency);
            }
            return new DefaultUsage(inputTokens, outputTokens, totalTokens, Collections.unmodifiableMap(nativeUsage), cacheTokens("cached_tokens"),
                    cacheTokens("cache_write_tokens"));
        }

        // Some gateways send a scalar or a malformed optional counter. The other counters and cost still apply.
        private @Nullable Long cacheTokens(String field) {
            if (inputDetails instanceof Map<?, ?> details && details.get(field) instanceof Number count && (count instanceof Long || count instanceof Integer)) {
                return count.longValue();
            }
            return null;
        }
    }
}
