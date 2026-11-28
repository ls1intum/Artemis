package de.tum.cit.aet.artemis.core.service.llm;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;

import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;

/** Keeps provider output used for stateless replay in sync with repairs to a Spring AI assistant message. */
public final class ResponsesOutputRepair {

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
        if (text == null || !assistant.getMetadata().containsKey(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY)) {
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
        if (!(metadata.get(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY) instanceof List<?> items)) {
            return metadata;
        }
        List<Object> repairedItems = new ArrayList<>(items.size());
        for (Object item : items) {
            repairedItems.add(repairItem(item, textRepair, nameRepair));
        }
        var repaired = new HashMap<>(metadata);
        repaired.put(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, List.copyOf(repairedItems));
        return repaired;
    }

    private static Object repairItem(Object original, @Nullable UnaryOperator<String> textRepair, @Nullable UnaryOperator<String> nameRepair) {
        if (!(original instanceof ResponseOutputItem item)) {
            return original;
        }
        if (item.isFunctionCall() && nameRepair != null) {
            var call = item.asFunctionCall();
            String name = nameRepair.apply(call.name());
            return name.equals(call.name()) ? item : ResponseOutputItem.ofFunctionCall(call.toBuilder().name(name).build());
        }
        if (item.isMessage() && textRepair != null) {
            var message = item.asMessage();
            var parts = new ArrayList<ResponseOutputMessage.Content>(message.content().size());
            for (var part : message.content()) {
                var outputText = part.asOutputText();
                String text = textRepair.apply(outputText.text());
                parts.add(text.equals(outputText.text()) ? part : ResponseOutputMessage.Content.ofOutputText(outputText.toBuilder().text(text).build()));
            }
            return parts.equals(message.content()) ? item : ResponseOutputItem.ofMessage(message.toBuilder().content(parts).build());
        }
        return item;
    }
}
