package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;

import com.knuddels.jtokkit.api.EncodingType;

final class AgentConversationContext {

    static final int MAX_TOOL_RESPONSE_CHARS = 12_000;

    static final int MESSAGE_OVERHEAD_TOKENS = 4;

    /** Per-tool-result truncation applied when serializing older messages as input to the summarizer. */
    private static final int SUMMARY_INPUT_TRUNCATE_CHARS = 2_000;

    private static final int TOOL_CALL_OVERHEAD_TOKENS = 8;

    private static final JTokkitTokenCountEstimator TOKEN_ESTIMATOR = new JTokkitTokenCountEstimator(EncodingType.O200K_BASE);

    private AgentConversationContext() {
    }

    static long estimateContextTokens(List<Message> conversation, long lastPromptTokens, int messagesAtLastCall) {
        return estimateContextTokens(conversation, lastPromptTokens, messagesAtLastCall, 0);
    }

    static long estimateContextTokens(List<Message> conversation, long lastPromptTokens, int messagesAtLastCall, long toolDefinitionTokens) {
        if (lastPromptTokens <= 0 || messagesAtLastCall < 0 || messagesAtLastCall > conversation.size()) {
            return estimateTokens(conversation, 0, conversation.size()) + toolDefinitionTokens;
        }
        // Provider input already includes the tool definitions; add only messages appended since that call.
        return lastPromptTokens + estimateTokens(conversation, messagesAtLastCall, conversation.size());
    }

    static long estimateToolDefinitionTokens(ToolCallback[] callbacks) {
        long tokens = 0;
        for (ToolCallback callback : callbacks) {
            var definition = callback.getToolDefinition();
            tokens += TOOL_CALL_OVERHEAD_TOKENS + estimateTextTokens(definition.name()) + estimateTextTokens(definition.description())
                    + estimateTextTokens(definition.inputSchema());
        }
        return tokens;
    }

    static long promptTokensOf(@Nullable ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return 0;
        }
        // Usage is null-marked, but a provider implementation can still return null; count that as zero rather than failing the run.
        return Objects.requireNonNullElse(response.getMetadata().getUsage().getPromptTokens(), 0).longValue();
    }

    static long estimateTokens(List<Message> conversation, int from, int to) {
        long tokens = 0;
        for (int i = from; i < to; i++) {
            tokens += estimateMessageTokens(conversation.get(i));
        }
        return tokens;
    }

    static long estimateMessageTokens(Message message) {
        long tokens = MESSAGE_OVERHEAD_TOKENS;
        if (message instanceof ToolResponseMessage toolResponse) {
            for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                tokens += TOOL_CALL_OVERHEAD_TOKENS + estimateTextTokens(response.responseData());
            }
            return tokens;
        }
        if (message instanceof AssistantMessage assistant) {
            tokens += estimateTextTokens(assistant.getText());
            for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                tokens += TOOL_CALL_OVERHEAD_TOKENS + estimateTextTokens(toolCall.name()) + estimateTextTokens(toolCall.arguments());
            }
            return tokens;
        }
        return tokens + estimateTextTokens(message.getText());
    }

    private static long estimateTextTokens(@Nullable String text) {
        return text == null || text.isEmpty() ? 0 : TOKEN_ESTIMATOR.estimate(text);
    }

    static void capToolResponses(List<Message> conversation) {
        for (int i = 0; i < conversation.size(); i++) {
            if (!(conversation.get(i) instanceof ToolResponseMessage toolResponse)) {
                continue;
            }
            boolean changed = false;
            List<ToolResponseMessage.ToolResponse> capped = new ArrayList<>(toolResponse.getResponses().size());
            for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                String data = response.responseData();
                if (data != null && data.length() > MAX_TOOL_RESPONSE_CHARS) {
                    capped.add(new ToolResponseMessage.ToolResponse(response.id(), response.name(), truncateMiddle(data)));
                    changed = true;
                }
                else {
                    capped.add(response);
                }
            }
            if (changed) {
                conversation.set(i, ToolResponseMessage.builder().responses(capped).metadata(toolResponse.getMetadata()).build());
            }
        }
    }

    private static String truncateMiddle(String data) {
        int head = MAX_TOOL_RESPONSE_CHARS / 4;
        int elidedEstimate = data.length() - MAX_TOOL_RESPONSE_CHARS;
        String marker = "\n[… " + elidedEstimate
                + " characters elided to fit the context window. Re-fetch just the part you need: read_file with offset/limit, or grep via bash. …]\n";
        int tail = Math.max(0, MAX_TOOL_RESPONSE_CHARS - head - marker.length());
        return data.substring(0, head) + marker + data.substring(data.length() - tail);
    }

    static String summaryTranscript(List<Message> messages) {
        StringBuilder transcript = new StringBuilder();
        for (Message message : messages) {
            transcript.append(renderForSummary(message)).append('\n');
        }
        return transcript.toString();
    }

    /** Renders one message as plain text for the summarizer, truncating long tool results and tool-call arguments so the summarization input itself stays bounded. */
    private static String renderForSummary(Message message) {
        if (message instanceof ToolResponseMessage toolResponse) {
            StringBuilder builder = new StringBuilder();
            for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                builder.append("TOOL RESULT (").append(response.name()).append("): ").append(truncateForSummary(response.responseData())).append('\n');
            }
            return builder.toString();
        }
        if (message instanceof AssistantMessage assistant) {
            StringBuilder builder = new StringBuilder();
            if (assistant.getText() != null && !assistant.getText().isBlank()) {
                builder.append("ASSISTANT: ").append(assistant.getText()).append('\n');
            }
            for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                builder.append("ASSISTANT TOOL CALL ").append(toolCall.name()).append(": ").append(truncateForSummary(toolCall.arguments())).append('\n');
            }
            return builder.toString();
        }
        String role = message instanceof SystemMessage ? "SYSTEM" : "USER";
        return role + ": " + (message.getText() == null ? "" : message.getText());
    }

    private static String truncateForSummary(@Nullable String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= SUMMARY_INPUT_TRUNCATE_CHARS) {
            return value;
        }
        return value.substring(0, SUMMARY_INPUT_TRUNCATE_CHARS) + " […" + (value.length() - SUMMARY_INPUT_TRUNCATE_CHARS) + " more characters truncated]";
    }

    static void assertValidPairing(List<Message> conversation) {
        for (int i = 0; i < conversation.size(); i++) {
            Message message = conversation.get(i);
            if (message instanceof ToolResponseMessage && (i == 0 || !(conversation.get(i - 1) instanceof AssistantMessage previous) || previous.getToolCalls().isEmpty())) {
                throw new IllegalStateException("Compaction produced an orphaned tool-result message at index " + i);
            }
            if (message instanceof AssistantMessage assistant && !assistant.getToolCalls().isEmpty()
                    && (i + 1 >= conversation.size() || !(conversation.get(i + 1) instanceof ToolResponseMessage))) {
                throw new IllegalStateException("Compaction left an assistant tool-call without a following tool-result at index " + i);
            }
        }
    }
}
