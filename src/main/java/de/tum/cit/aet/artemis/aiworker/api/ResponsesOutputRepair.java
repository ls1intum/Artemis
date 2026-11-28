package de.tum.cit.aet.artemis.aiworker.api;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JsonPointer;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/** Keeps provider output used for stateless replay in sync with repairs to a Spring AI assistant message. */
public final class ResponsesOutputRepair {

    /** Ordered provider output retained on the assistant message for the next Responses request. */
    public static final String OUTPUT_ITEMS_METADATA_KEY = "openai.responses.output-items";

    private static final JsonPointer NAME = JsonPointer.compile("/name");

    private static final JsonPointer TEXT = JsonPointer.compile("/text");

    private ResponsesOutputRepair() {
    }

    /**
     * Removes matches from the original output_text parts, including a match split across part or message boundaries.
     *
     * @param assistant the original assistant message
     * @param pattern   the tokens removed from its text
     * @return assistant metadata with the same deletions applied to the ordered replay
     */
    public static Map<String, Object> removeTextMatches(AssistantMessage assistant, Pattern pattern) {
        String text = assistant.getText();
        if (text == null || !assistant.getMetadata().containsKey(OUTPUT_ITEMS_METADATA_KEY)) {
            return assistant.getMetadata();
        }
        var removed = new BitSet();
        pattern.matcher(text).results().forEach(match -> removed.set(match.start(), match.end()));
        return repairMetadata(assistant, new UnaryOperator<>() {

            private int offset;

            @Override
            public String apply(String part) {
                var kept = new StringBuilder(part.length());
                for (int i = 0; i < part.length(); i++) {
                    if (!removed.get(offset + i)) {
                        kept.append(part.charAt(i));
                    }
                }
                offset += part.length();
                return kept.toString();
            }
        }, null);
    }

    /**
     * Applies the same tool-name repair to the ordered function calls used for replay.
     *
     * @param assistant the message whose tool calls were normalized
     * @param renamed   original tool names mapped to their replacements
     * @return assistant metadata with the repaired function names
     */
    public static Map<String, Object> renameTools(AssistantMessage assistant, Map<String, String> renamed) {
        return repairMetadata(assistant, null, name -> renamed.getOrDefault(name, name));
    }

    private static Map<String, Object> repairMetadata(AssistantMessage assistant, @Nullable UnaryOperator<String> textRepair, @Nullable UnaryOperator<String> nameRepair) {
        var metadata = assistant.getMetadata();
        if (!(metadata.get(OUTPUT_ITEMS_METADATA_KEY) instanceof List<?> items)) {
            return metadata;
        }
        List<Object> repairedItems = new ArrayList<>(items.size());
        for (Object item : items) {
            repairedItems.add(repairItem(item, textRepair, nameRepair));
        }
        var repaired = new HashMap<>(metadata);
        repaired.put(OUTPUT_ITEMS_METADATA_KEY, List.copyOf(repairedItems));
        return repaired;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemType(String type) {
    }

    private static Object repairItem(Object original, @Nullable UnaryOperator<String> textRepair, @Nullable UnaryOperator<String> nameRepair) {
        if (!(original instanceof String json)) {
            return original;
        }
        JsonMapper mapper = JsonObjectMapper.get();
        String type = mapper.readValue(json, ItemType.class).type();
        if ("message".equals(type) && textRepair != null) {
            return replaceStrings(json, mapper, ResponsesOutputRepair::isContentText, textRepair);
        }
        if ("function_call".equals(type) && nameRepair != null) {
            return replaceStrings(json, mapper, NAME::equals, nameRepair);
        }
        return json;
    }

    private static String replaceStrings(String json, JsonMapper mapper, Predicate<JsonPointer> field, UnaryOperator<String> repair) {
        // Splice only the repaired JSON string values. Unknown provider fields, including numeric precision, stay verbatim.
        StringBuilder result = new StringBuilder();
        int copied = 0;
        try (var parser = mapper.createParser(json)) {
            while (parser.nextToken() != null) {
                if (!parser.hasToken(JsonToken.VALUE_STRING) || !field.test(parser.streamReadContext().pathAsPointer())) {
                    continue;
                }
                String value = parser.getString();
                String replacement = repair.apply(value);
                if (!value.equals(replacement)) {
                    result.append(json, copied, Math.toIntExact(parser.currentTokenLocation().getCharOffset())).append(mapper.writeValueAsString(replacement));
                    copied = Math.toIntExact(parser.currentLocation().getCharOffset());
                }
            }
        }
        return copied == 0 ? json : result.append(json, copied, json.length()).toString();
    }

    private static boolean isContentText(JsonPointer path) {
        JsonPointer content = path.matchProperty("content");
        return content != null && content.getMatchingIndex() >= 0 && TEXT.equals(content.tail());
    }
}
