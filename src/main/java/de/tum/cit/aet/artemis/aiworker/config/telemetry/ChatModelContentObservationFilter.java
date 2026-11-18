package de.tum.cit.aet.artemis.aiworker.config.telemetry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.json.JsonMapper;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * Classifies Spring AI chat spans and optionally enriches them with OpenTelemetry GenAI message content.
 */
public class ChatModelContentObservationFilter implements ObservationFilter {

    private static final Logger log = LoggerFactory.getLogger(ChatModelContentObservationFilter.class);

    private static final String INPUT_MESSAGES = "gen_ai.input.messages";

    private static final String OUTPUT_MESSAGES = "gen_ai.output.messages";

    private static final String TOOL_DEFINITIONS = "gen_ai.tool.definitions";

    private static final String CONTENT_COMPLETE = "artemis.gen_ai.content.complete";

    /**
     * Under {@code artemis.*} rather than {@code management.opentelemetry.instrumentation.*}: that namespace belongs to Spring Boot, so an Artemis key there gets no configuration
     * metadata and a future Boot release could give the same name a different meaning.
     */
    static final String MAX_ATTRIBUTE_BYTES_PROPERTY = "artemis.telemetry.gen-ai.max-attribute-bytes";

    /**
     * Default ceiling for one serialized GenAI content attribute, in UTF-8 bytes.
     * <p>
     * Sized against what the feature emits: a saturated 256,000-token context ({@code artemis.hyperion.agent.context-window-tokens}) is roughly 768,000 characters at the ~3
     * characters per token of source-heavy content, plus about a quarter again for JSON structure and escaping. This is double that, leaving room for multi-byte content and for a
     * deployment that pins a larger window. The bound exists because one pathological attribute can push an OTLP export batch past the collector's receive limit (4 MiB by
     * default) and take every span in that batch down with it; it does not replace collector-side batch sizing, which applies per batch rather than per attribute.
     */
    static final int DEFAULT_MAX_ATTRIBUTE_BYTES = 2_000_000;

    /** Spring AI's provider-neutral metadata key for reasoning content explicitly returned by an OpenAI-compatible provider. */
    private static final String REASONING_CONTENT = "reasoningContent";

    private final JsonMapper objectMapper;

    private final boolean captureContent;

    private final int maxAttributeBytes;

    public ChatModelContentObservationFilter(JsonMapper objectMapper, boolean captureContent, int maxAttributeBytes) {
        this.objectMapper = objectMapper;
        this.captureContent = captureContent;
        if (maxAttributeBytes <= 0) {
            log.error("Ignoring {}={}: the bound must be positive, otherwise every span would lose its content. Falling back to {} bytes.", MAX_ATTRIBUTE_BYTES_PROPERTY,
                    maxAttributeBytes, DEFAULT_MAX_ATTRIBUTE_BYTES);
        }
        this.maxAttributeBytes = maxAttributeBytes > 0 ? maxAttributeBytes : DEFAULT_MAX_ATTRIBUTE_BYTES;
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatContext)) {
            return context;
        }

        chatContext.addLowCardinalityKeyValue(KeyValue.of("ai.span", "true"));
        if (!captureContent) {
            return chatContext;
        }
        boolean complete = false;
        try {
            complete = addSerialized(chatContext, INPUT_MESSAGES, inputMessages(chatContext)) & addSerialized(chatContext, OUTPUT_MESSAGES, outputMessages(chatContext))
                    & addSerialized(chatContext, TOOL_DEFINITIONS, toolDefinitions(chatContext));
        }
        catch (RuntimeException exception) {
            log.warn("Could not enrich a Spring AI span with message content", exception);
        }
        chatContext.addHighCardinalityKeyValue(KeyValue.of(CONTENT_COMPLETE, Boolean.toString(complete)));
        return chatContext;
    }

    private List<GenAiMessage> inputMessages(ChatModelObservationContext context) {
        if (context.getRequest() == null || CollectionUtils.isEmpty(context.getRequest().getInstructions())) {
            return List.of();
        }
        return context.getRequest().getInstructions().stream().map(this::message).toList();
    }

    private List<GenAiMessage> outputMessages(ChatModelObservationContext context) {
        if (context.getResponse() == null || CollectionUtils.isEmpty(context.getResponse().getResults())) {
            return List.of();
        }
        return context.getResponse().getResults().stream().filter(generation -> generation.getOutput() != null).map(this::outputMessage).toList();
    }

    private List<GenAiToolDefinition> toolDefinitions(ChatModelObservationContext context) {
        if (context.getRequest() == null || !(context.getRequest().getOptions() instanceof ToolCallingChatOptions options) || CollectionUtils.isEmpty(options.getToolCallbacks())) {
            return List.of();
        }
        return options.getToolCallbacks().stream().map(this::toolDefinition).toList();
    }

    private GenAiToolDefinition toolDefinition(ToolCallback callback) {
        var definition = callback.getToolDefinition();
        String description = StringUtils.hasText(definition.description()) ? definition.description() : null;
        String parameters = StringUtils.hasText(definition.inputSchema()) ? embeddableJson(definition.inputSchema()) : null;
        return new GenAiToolDefinition("function", definition.name(), description, parameters);
    }

    private GenAiMessage message(Message message) {
        return new GenAiMessage(message.getMessageType().getValue(), messageParts(message), null);
    }

    private GenAiMessage outputMessage(Generation generation) {
        GenAiMessage message = message(generation.getOutput());
        String finishReason = generation.getMetadata().getFinishReason();
        return new GenAiMessage(message.role(), message.parts(), StringUtils.hasText(finishReason) ? finishReason : "unknown");
    }

    private List<GenAiPart> messageParts(Message message) {
        List<GenAiPart> parts = new ArrayList<>();
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.getMetadata().get(REASONING_CONTENT) instanceof String reasoning
                && StringUtils.hasText(reasoning)) {
            parts.add(new GenAiContentPart("reasoning", reasoning));
        }
        if (StringUtils.hasText(message.getText())) {
            parts.add(new GenAiContentPart("text", message.getText()));
        }
        if (message instanceof AssistantMessage assistantMessage) {
            assistantMessage.getToolCalls().stream().map(this::toolCallPart).forEach(parts::add);
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            toolResponseMessage.getResponses().stream()
                    .map(response -> new GenAiToolResponsePart("tool_call_response", StringUtils.hasText(response.id()) ? response.id() : null, response.responseData()))
                    .forEach(parts::add);
        }
        return parts;
    }

    private GenAiToolCallPart toolCallPart(AssistantMessage.ToolCall toolCall) {
        String id = StringUtils.hasText(toolCall.id()) ? toolCall.id() : null;
        String arguments = StringUtils.hasText(toolCall.arguments()) ? embeddableJson(toolCall.arguments()) : null;
        return new GenAiToolCallPart("tool_call", id, toolCall.name(), arguments);
    }

    /**
     * Turns text that is probably JSON into JSON text that can be embedded as a value of a span attribute. Text that is exactly one JSON value is embedded as that
     * value, so a tool schema or the arguments of a tool call stay structured for the trace viewer; any other text is embedded as a JSON string.
     *
     * @param text the schema or the arguments as the model or the tool declared them
     * @return JSON text of one valid JSON value
     */
    private String embeddableJson(String text) {
        if (isSingleJsonValue(text)) {
            return text;
        }
        return objectMapper.writeValueAsString(text);
    }

    /**
     * Checks that the text consists of exactly one JSON value, without building a tree of it.
     *
     * @param text the text to check
     * @return whether the text is one complete JSON value with nothing after it
     */
    private boolean isSingleJsonValue(String text) {
        try (JsonParser parser = objectMapper.createParser(text)) {
            if (parser.nextToken() == null) {
                return false;
            }
            parser.skipChildren();
            return parser.nextToken() == null;
        }
        catch (JacksonException e) {
            return false;
        }
    }

    /**
     * One message of the OpenTelemetry GenAI message attributes.
     *
     * @param role         the role of the message author
     * @param parts        the content parts of the message in order
     * @param finishReason why the model stopped, present on output messages only
     */
    private record GenAiMessage(String role, List<GenAiPart> parts, @JsonProperty("finish_reason") @JsonInclude(JsonInclude.Include.NON_EMPTY) String finishReason) {
    }

    /** A content part of a GenAI message. */
    private interface GenAiPart {
    }

    /**
     * A reasoning or text part of a GenAI message.
     *
     * @param type    {@code reasoning} or {@code text}
     * @param content the text of the part
     */
    private record GenAiContentPart(String type, String content) implements GenAiPart {
    }

    /**
     * A tool call the model issued.
     *
     * @param type      always {@code tool_call}
     * @param id        the identifier of the call, absent when the provider sent none
     * @param name      the name of the tool
     * @param arguments the arguments of the call as JSON text, written into the attribute as the JSON value it contains
     */
    private record GenAiToolCallPart(String type, @JsonInclude(JsonInclude.Include.NON_EMPTY) String id, String name,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonRawValue String arguments) implements GenAiPart {
    }

    /**
     * The result a tool returned for a tool call.
     *
     * @param type     always {@code tool_call_response}
     * @param id       the identifier of the call it answers, absent when the provider sent none
     * @param response the response of the tool
     */
    private record GenAiToolResponsePart(String type, @JsonInclude(JsonInclude.Include.NON_EMPTY) String id, String response) implements GenAiPart {
    }

    /**
     * A tool definition in the OpenTelemetry GenAI tool definition attribute.
     *
     * @param type        always {@code function}
     * @param name        the name of the tool
     * @param description what the tool does, absent when the tool declares none
     * @param parameters  the input schema of the tool as JSON text, written into the attribute as the JSON value it contains
     */
    private record GenAiToolDefinition(String type, String name, @JsonInclude(JsonInclude.Include.NON_EMPTY) String description,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonRawValue String parameters) {
    }

    /** The span identifier an operator sees in the trace viewer. */
    private static String spanName(ChatModelObservationContext context) {
        String contextualName = context.getContextualName();
        return StringUtils.hasText(contextualName) ? contextualName : String.valueOf(context.getName());
    }

    private boolean addSerialized(ChatModelObservationContext context, String key, List<?> messages) {
        if (messages.isEmpty()) {
            return true;
        }
        try {
            String value = objectMapper.writeValueAsString(messages);
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxAttributeBytes) {
                log.warn("Omitting the {} OpenTelemetry attribute of chat span '{}': {} bytes exceeds the {}-byte limit. The span is marked {}=false; raise {} to keep it.", key,
                        spanName(context), bytes, maxAttributeBytes, CONTENT_COMPLETE, MAX_ATTRIBUTE_BYTES_PROPERTY);
                return false;
            }
            context.addHighCardinalityKeyValue(KeyValue.of(key, value));
            return true;
        }
        catch (JacksonException e) {
            log.error("Could not serialize Spring AI message content for OpenTelemetry", e);
            return false;
        }
    }
}
