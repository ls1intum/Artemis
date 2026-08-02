package de.tum.cit.aet.artemis.core.config;

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
public class ChatModelContentObservationFilter implements ObservationFilter {

    private static final Logger log = LoggerFactory.getLogger(ChatModelContentObservationFilter.class);

    private static final String INPUT_MESSAGES = "gen_ai.input.messages";

    private static final String OUTPUT_MESSAGES = "gen_ai.output.messages";

    private static final String TOOL_DEFINITIONS = "gen_ai.tool.definitions";

    private static final String CONTENT_COMPLETE = "artemis.gen_ai.content.complete";

    private static final int MAX_ATTRIBUTE_LENGTH = 64_000;

    /** Spring AI's provider-neutral metadata key for reasoning content explicitly returned by an OpenAI-compatible provider. */
    private static final String REASONING_CONTENT = "reasoningContent";

    private final ObjectMapper objectMapper;

    private final boolean captureContent;

    public ChatModelContentObservationFilter(ObjectMapper objectMapper, @Value("${management.opentelemetry.instrumentation.gen-ai.capture-content:false}") boolean captureContent) {
        this.objectMapper = objectMapper;
        this.captureContent = captureContent;
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

    private boolean addSerialized(ChatModelObservationContext context, String key, List<Map<String, Object>> messages) {
        if (messages.isEmpty()) {
            return true;
        }
        try {
            String value = objectMapper.writeValueAsString(messages);
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_ATTRIBUTE_LENGTH) {
                log.warn("Omitting oversized {} OpenTelemetry attribute ({} bytes)", key, bytes);
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
