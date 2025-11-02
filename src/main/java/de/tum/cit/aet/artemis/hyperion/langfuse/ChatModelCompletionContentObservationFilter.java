package de.tum.cit.aet.artemis.hyperion.langfuse;

import java.util.List;

import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;

/**
 * Observation filter that enriches AI chat model spans with additional metadata
 * such as prompts and completions for Langfuse tracing.
 *
 * <p>
 * This filter is automatically applied to {@link ChatModelObservationContext}
 * instances and marks them with {@code ai.span=true}, so that only AI spans
 * are exported by the custom {@code LlmOnlyExporterConfig}.
 */
@Component
public class ChatModelCompletionContentObservationFilter implements io.micrometer.observation.ObservationFilter {

    /**
     * Adds prompt and completion data to AI spans and marks them with {@code ai.span=true}.
     *
     * @param context the Micrometer observation context to inspect and enrich
     * @return the modified observation context (or the original if not applicable)
     */
    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
            return context;
        }

        var prompts = processPrompts(chatModelObservationContext);
        var completions = processCompletion(chatModelObservationContext);
        chatModelObservationContext.addLowCardinalityKeyValue(kv("ai.span", "true"));
        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {

            @Override
            public String getKey() {
                return "gen_ai.prompt";
            }

            @Override
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(prompts);
            }
        });

        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {

            @Override
            public String getKey() {
                return "gen_ai.completion";
            }

            @Override
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(completions);
            }
        });

        return chatModelObservationContext;
    }

    /**
     * Extracts all prompt texts from the given {@link ChatModelObservationContext}.
     *
     * @param chatModelObservationContext the chat model context containing request instructions
     * @return immutable list of prompt text strings
     */
    private List<String> processPrompts(ChatModelObservationContext chatModelObservationContext) {
        return CollectionUtils.isEmpty((chatModelObservationContext.getRequest()).getInstructions()) ? List.of()
                : (chatModelObservationContext.getRequest()).getInstructions().stream().map(Content::getText).toList();
    }

    /**
     * Extracts completion texts from the AI model response, ignoring empty or null outputs.
     *
     * @param context the chat model context containing the model response
     * @return immutable list of non-empty completion strings
     */
    private List<String> processCompletion(ChatModelObservationContext context) {
        if (context.getResponse() != null && (context.getResponse()).getResults() != null && !CollectionUtils.isEmpty((context.getResponse()).getResults())) {
            return !StringUtils.hasText((context.getResponse()).getResult().getOutput().getText()) ? List.of()
                    : (context.getResponse()).getResults().stream().filter((generation) -> generation.getOutput() != null && StringUtils.hasText(generation.getOutput().getText()))
                            .map((generation) -> generation.getOutput().getText()).toList();
        }
        else {
            return List.of();
        }
    }

    /**
     * Utility method for constructing {@link KeyValue} pairs for Micrometer observations.
     *
     * @param key   the attribute name
     * @param value the attribute value
     * @return immutable {@link KeyValue} instance with the given key and value
     */
    private static KeyValue kv(String key, String value) {
        return new KeyValue() {

            @Override
            public String getKey() {
                return key;
            }

            @Override
            public String getValue() {
                return value;
            }
        };
    }
}
