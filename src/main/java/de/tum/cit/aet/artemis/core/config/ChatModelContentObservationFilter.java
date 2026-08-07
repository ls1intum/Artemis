package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.util.Map.entry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * Classifies Spring AI chat spans and optionally enriches them with OpenTelemetry GenAI message content.
 */
@Lazy
@Component
@Profile(PROFILE_CORE)
public class ChatModelContentObservationFilter implements ObservationFilter {

    private static final Logger log = LoggerFactory.getLogger(ChatModelContentObservationFilter.class);

    private static final String INPUT_MESSAGES = "gen_ai.input.messages";

    private static final String OUTPUT_MESSAGES = "gen_ai.output.messages";

    private static final String TOOL_DEFINITIONS = "gen_ai.tool.definitions";

    private static final String CONTENT_COMPLETE = "artemis.gen_ai.content.complete";

    static final String MAX_ATTRIBUTE_BYTES_PROPERTY = "management.opentelemetry.instrumentation.gen-ai.max-attribute-bytes";

    /**
     * Default ceiling for one serialized GenAI content attribute, in UTF-8 bytes.
     * <p>
     * Derived from what this feature actually emits rather than from a round number. The largest context window the shipped configuration contemplates is 256,000 tokens
     * ({@code artemis.hyperion.agent.context-window-tokens}, whose per-profile example pins exactly that). Source-heavy agentic context runs near three characters per token, so
     * a saturated context is roughly 768,000 characters, and JSON structure plus escaping adds about a quarter again — call it 960,000 bytes. This is double that, leaving room
     * for multi-byte content and for a deployment that pins a larger window before it has to touch the property.
     * <p>
     * The predecessor filter had no ceiling at all and ran in production for months with observed attributes up to ~220,000 characters, so a bound is a precaution, not a
     * requirement. It is kept because one pathological attribute can push an OTLP export batch past the collector's receive limit (the OpenTelemetry Collector's OTLP receiver
     * defaults to 4 MiB) and take every span in that batch down with it — losing measurement silently, which is the same harm this filter exists to prevent. It is deliberately
     * <em>not</em> a substitute for collector-side batch sizing: that limit applies per export batch, not per attribute, and remains an operator concern.
     */
    static final int DEFAULT_MAX_ATTRIBUTE_BYTES = 2_000_000;

    /** Spring AI's provider-neutral metadata key for reasoning content explicitly returned by an OpenAI-compatible provider. */
    private static final String REASONING_CONTENT = "reasoningContent";

    private final ObjectMapper objectMapper;

    private final boolean captureContent;

    private final int maxAttributeBytes;

    public ChatModelContentObservationFilter(ObjectMapper objectMapper, @Value("${management.opentelemetry.instrumentation.gen-ai.capture-content:false}") boolean captureContent,
            @Value("${" + MAX_ATTRIBUTE_BYTES_PROPERTY + ":" + DEFAULT_MAX_ATTRIBUTE_BYTES + "}") int maxAttributeBytes) {
        this.objectMapper = objectMapper;
        this.captureContent = captureContent;
        // Deliberately falls back instead of throwing. This bean is @Lazy, so a constructor exception would not fail startup — it would surface at the first span, inside a
        // provider call, and break the generation this filter only exists to observe. A tracing misconfiguration must never be able to do that, which is the same rule that
        // makes map() swallow serialization failures. A non-positive bound would also omit every attribute, silently reproducing the measurement loss this property exists to
        // let an operator escape, so it is refused loudly and the shipped default is used.
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

    private List<Map<String, Object>> inputMessages(ChatModelObservationContext context) {
        if (context.getRequest() == null || CollectionUtils.isEmpty(context.getRequest().getInstructions())) {
            return List.of();
        }
        return context.getRequest().getInstructions().stream().map(this::message).toList();
    }

    private List<Map<String, Object>> outputMessages(ChatModelObservationContext context) {
        if (context.getResponse() == null || CollectionUtils.isEmpty(context.getResponse().getResults())) {
            return List.of();
        }
        return context.getResponse().getResults().stream().filter(generation -> generation.getOutput() != null).map(this::outputMessage).toList();
    }

    private List<Map<String, Object>> toolDefinitions(ChatModelObservationContext context) {
        if (context.getRequest() == null || !(context.getRequest().getOptions() instanceof ToolCallingChatOptions options) || CollectionUtils.isEmpty(options.getToolCallbacks())) {
            return List.of();
        }
        return options.getToolCallbacks().stream().map(this::toolDefinition).toList();
    }

    private Map<String, Object> toolDefinition(ToolCallback callback) {
        var definition = callback.getToolDefinition();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "function");
        result.put("name", definition.name());
        if (StringUtils.hasText(definition.description())) {
            result.put("description", definition.description());
        }
        if (StringUtils.hasText(definition.inputSchema())) {
            try {
                result.put("parameters", objectMapper.readTree(definition.inputSchema()));
            }
            catch (JsonProcessingException e) {
                result.put("parameters", definition.inputSchema());
            }
        }
        return result;
    }

    private Map<String, Object> message(Message message) {
        return Map.ofEntries(entry("role", message.getMessageType().getValue()), entry("parts", messageParts(message)));
    }

    private Map<String, Object> outputMessage(Generation generation) {
        Map<String, Object> result = new LinkedHashMap<>(message(generation.getOutput()));
        String finishReason = generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason();
        result.put("finish_reason", StringUtils.hasText(finishReason) ? finishReason : "unknown");
        return result;
    }

    private List<Map<String, Object>> messageParts(Message message) {
        List<Map<String, Object>> parts = new ArrayList<>();
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.getMetadata().get(REASONING_CONTENT) instanceof String reasoning
                && StringUtils.hasText(reasoning)) {
            parts.add(Map.of("type", "reasoning", "content", reasoning));
        }
        if (StringUtils.hasText(message.getText())) {
            parts.add(Map.of("type", "text", "content", message.getText()));
        }
        if (message instanceof AssistantMessage assistantMessage) {
            assistantMessage.getToolCalls().stream().map(this::toolCallPart).forEach(parts::add);
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            toolResponseMessage.getResponses().stream().map(response -> {
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("type", "tool_call_response");
                if (StringUtils.hasText(response.id())) {
                    part.put("id", response.id());
                }
                part.put("response", response.responseData());
                return part;
            }).forEach(parts::add);
        }
        return parts;
    }

    private Map<String, Object> toolCallPart(AssistantMessage.ToolCall toolCall) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "tool_call");
        if (StringUtils.hasText(toolCall.id())) {
            part.put("id", toolCall.id());
        }
        part.put("name", toolCall.name());
        if (StringUtils.hasText(toolCall.arguments())) {
            try {
                part.put("arguments", objectMapper.readTree(toolCall.arguments()));
            }
            catch (JsonProcessingException e) {
                part.put("arguments", toolCall.arguments());
            }
        }
        return part;
    }

    /** The span identifier an operator actually sees in the trace backend, falling back to the observation name when no contextual name was set. */
    private static String spanName(ChatModelObservationContext context) {
        String contextualName = context.getContextualName();
        return StringUtils.hasText(contextualName) ? contextualName : String.valueOf(context.getName());
    }

    private boolean addSerialized(ChatModelObservationContext context, String key, List<Map<String, Object>> messages) {
        if (messages.isEmpty()) {
            return true;
        }
        try {
            String value = objectMapper.writeValueAsString(messages);
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxAttributeBytes) {
                // Everything an operator needs to act, on one line: which attribute, which span it belonged to, how far over it was without subtracting anything themselves,
                // the marker that makes the affected spans findable in the trace backend, and the property to raise.
                log.warn("Omitting the {} OpenTelemetry attribute of chat span '{}': {} bytes exceeds the {}-byte limit. The span is marked {}=false; raise {} to keep it.", key,
                        spanName(context), bytes, maxAttributeBytes, CONTENT_COMPLETE, MAX_ATTRIBUTE_BYTES_PROPERTY);
                return false;
            }
            context.addHighCardinalityKeyValue(KeyValue.of(key, value));
            return true;
        }
        catch (JsonProcessingException e) {
            log.error("Could not serialize Spring AI message content for OpenTelemetry", e);
            return false;
        }
    }
}
