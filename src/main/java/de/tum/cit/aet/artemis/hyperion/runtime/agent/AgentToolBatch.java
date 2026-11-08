package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.util.JsonHelper;

/**
 * How {@link AgentLoopRunner} reads and answers one batch of model tool calls. Before execution it repairs leaked tool names, validates the batch, and recognizes a completion
 * the output token limit cut off; after execution it classifies the results, escalates an identical call the model keeps repeating, and names the rejection that ended a
 * session. Stateless: the loop owns the conversation and the per-session repeat counts and passes them in.
 */
final class AgentToolBatch {

    private static final Logger log = LoggerFactory.getLogger(AgentToolBatch.class);

    static final String SUBMIT_TOOL_NAME = "submit";

    private static final String VERIFY_TOOL_NAME = "verify";

    /**
     * The occurrence of an identical tool call that is answered explicitly: the same tool and arguments when it was rejected, or the same tool, arguments and result when it
     * succeeded. A repeated rejection is replaced by an escalation and counts toward ending the session; a repeated observation keeps its full result behind a note, and the turn
     * budget bounds it.
     */
    static final int REPEATED_REJECTION_ESCALATION = 3;

    private static final int MAX_REPEATED_REJECTION_EXCERPT_CHARS = 400;

    /**
     * Tools that change the workspace. Their successful result does not show whether the workspace changed in between, so identical successes are not repeats, and a success
     * means earlier observations may be out of date.
     */
    private static final Set<String> WORKSPACE_MUTATING_TOOLS = Set.of("write_file", "edit_file", "delete_file");

    private static final String REJECTED_CALL_KEY = "rejected\n";

    private static final String OBSERVED_CALL_KEY = "observed\n";

    private static final JsonHelper TOOL_ARGUMENT_JSON = new JsonHelper();

    private AgentToolBatch() {
    }

    /**
     * Removes leaked harmony control tokens from tool-call names, so a name like {@code bash<|channel|>commentary} dispatches as {@code bash} instead of matching no registered
     * tool.
     */
    static ChatResponse normalizeToolNames(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return response;
        }
        AssistantMessage output = response.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        if (toolCalls.isEmpty()) {
            return response;
        }
        boolean changed = false;
        List<AssistantMessage.ToolCall> normalized = new ArrayList<>(toolCalls.size());
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            String sanitized = sanitizeToolName(toolCall.name());
            if (!sanitized.equals(toolCall.name())) {
                changed = true;
                log.warn("Normalized a leaked model tool name to '{}'", sanitized);
                normalized.add(new AssistantMessage.ToolCall(toolCall.id(), toolCall.type(), sanitized, toolCall.arguments()));
            }
            else {
                normalized.add(toolCall);
            }
        }
        if (!changed) {
            return response;
        }
        AssistantMessage rebuilt = AssistantMessage.builder().content(output.getText() == null ? "" : output.getText()).properties(output.getMetadata()).media(output.getMedia())
                .toolCalls(normalized).build();
        return new ChatResponse(List.of(new Generation(rebuilt, response.getResult().getMetadata())), response.getMetadata());
    }

    static String sanitizeToolName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (!name.contains("<|")) {
            return name.strip();
        }
        // Everything before the first control token is the real tool name; the rest is leakage.
        return name.substring(0, name.indexOf("<|")).strip();
    }

    static String preflightRejectionFeedback(ToolCallback[] callbacks) {
        List<String> available = java.util.Arrays.stream(callbacks).map(callback -> callback.getToolDefinition().name()).sorted().toList();
        return "ERROR: no call in this batch was executed. Use only these available tools: " + available
                + ". Supply valid JSON object arguments matching the selected tool's schema, then retry.";
    }

    static void validateToolBatch(List<AssistantMessage.ToolCall> calls, ToolCallback[] callbacks) {
        for (AssistantMessage.ToolCall call : calls) {
            boolean available = false;
            for (ToolCallback callback : callbacks) {
                if (callback.getToolDefinition().name().equals(call.name())) {
                    available = true;
                    break;
                }
            }
            if (!available || call.arguments() == null || !call.arguments().stripLeading().startsWith("{")) {
                throw new IllegalArgumentException("Unknown tool or invalid arguments");
            }
            TOOL_ARGUMENT_JSON.fromJsonToMap(call.arguments());
        }
    }

    /**
     * Answers every call of a batch that was not executed with the same outcome, because the chat-completions tool-pairing contract requires a result for every requested call
     * id.
     */
    static ToolResponseMessage unexecutedResponses(List<AssistantMessage.ToolCall> toolCalls, String outcome) {
        List<ToolResponseMessage.ToolResponse> responses = toolCalls.stream().map(toolCall -> new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), outcome))
                .toList();
        return ToolResponseMessage.builder().responses(responses).build();
    }

    static boolean hasRejectedToolAction(List<Message> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i) instanceof ToolResponseMessage toolResponse) {
                return toolResponse.getResponses().stream().anyMatch(response -> !VERIFY_TOOL_NAME.equals(response.name()) && isErrorResponse(response.responseData()));
            }
        }
        return false;
    }

    /**
     * Counts the calls of the latest tool batch and answers each call repeated {@link #REPEATED_REJECTION_ESCALATION} or more times explicitly. A rejected call is identified by
     * tool and arguments and its result is replaced by an escalation; a successful call also by its result, so re-reading a file after an edit is not a repeat, and it keeps its
     * full result behind a note. A model that interleaves the same call with other calls never trips the consecutive limit, so without this it can spend a whole session
     * repeating it. A successful write, edit or delete may change what an earlier observation showed, so it restarts the count of every successful call.
     *
     * @return whether the batch repeated a rejected call; one batch counts at most once toward ending the session, so the model always reads an escalation before the next counts
     */
    static boolean escalateRepeatedRejections(List<Message> conversation, List<AssistantMessage.ToolCall> toolCalls, Map<String, Integer> repeatedCallCounts) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i) instanceof ToolResponseMessage toolResponse) {
                if (toolResponse.getResponses().stream().anyMatch(AgentToolBatch::changedWorkspace)) {
                    forgetObservations(repeatedCallCounts);
                }
                boolean repeatedRejection = false;
                boolean replaced = false;
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>(toolResponse.getResponses().size());
                for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                    if (VERIFY_TOOL_NAME.equals(response.name()) || SUBMIT_TOOL_NAME.equals(response.name())) {
                        responses.add(response);
                        continue;
                    }
                    boolean rejected = isErrorResponse(response.responseData());
                    if (changedWorkspace(response)) {
                        // A write reports only what it wrote, not whether the file had changed since the last identical write, so repeating it is not evidence of a loop.
                        responses.add(response);
                        continue;
                    }
                    String arguments = toolCalls.stream().filter(call -> call.id() != null && call.id().equals(response.id())).map(AssistantMessage.ToolCall::arguments).findFirst()
                            .orElse("");
                    String key = rejected ? REJECTED_CALL_KEY + response.name() + '\n' + arguments
                            : OBSERVED_CALL_KEY + response.name() + '\n' + arguments + '\n' + response.responseData();
                    int occurrences = repeatedCallCounts.merge(key, 1, Integer::sum);
                    if (occurrences < REPEATED_REJECTION_ESCALATION) {
                        responses.add(response);
                        continue;
                    }
                    replaced = true;
                    repeatedRejection |= rejected;
                    // A repeated observation is not a rejection: the note must not start with ERROR, or it would count toward the consecutive-failure limit. It keeps the full
                    // result, because a re-read can be legitimate, for example after compaction dropped the earlier one, and a shell command may have changed the workspace.
                    String escalation = rejected
                            ? "ERROR: this exact " + response.name() + " call has now been rejected " + occurrences
                                    + " times. Repeating it unchanged will not help; take a different action. Each further repeat counts toward ending this "
                                    + "session. The rejection was: " + excerpt(response.responseData().strip())
                            : "NOTE: this exact " + response.name() + " call has now returned the same result " + occurrences
                                    + " times. Unless you expect a different result, take a different action instead of repeating it. The result was:\n" + response.responseData();
                    responses.add(new ToolResponseMessage.ToolResponse(response.id(), response.name(), escalation));
                }
                if (replaced) {
                    conversation.set(i, ToolResponseMessage.builder().responses(responses).metadata(toolResponse.getMetadata()).build());
                }
                return repeatedRejection;
            }
        }
        return false;
    }

    /**
     * Restarts the count of every successful call, keeping the count of rejected calls: once the workspace or the conversation changed, an identical result no longer shows that
     * the model is repeating itself without reason.
     */
    static void forgetObservations(Map<String, Integer> repeatedCallCounts) {
        repeatedCallCounts.keySet().removeIf(key -> key.startsWith(OBSERVED_CALL_KEY));
    }

    static boolean hasOnlyRecoverableFileToolRejections(List<Message> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i) instanceof ToolResponseMessage toolResponse) {
                List<ToolResponseMessage.ToolResponse> rejected = toolResponse.getResponses().stream()
                        .filter(response -> !VERIFY_TOOL_NAME.equals(response.name()) && isErrorResponse(response.responseData())).toList();
                return !rejected.isEmpty() && rejected.stream().allMatch(response -> "edit_file".equals(response.name()) || "search".equals(response.name()));
            }
        }
        return false;
    }

    private static boolean changedWorkspace(ToolResponseMessage.ToolResponse response) {
        if (!WORKSPACE_MUTATING_TOOLS.contains(response.name()) || isErrorResponse(response.responseData())) {
            return false;
        }
        String result = response.responseData() == null ? "" : response.responseData().stripLeading();
        return !result.startsWith("No change:") && !result.startsWith("\"No change:");
    }

    private static boolean isErrorResponse(@Nullable String responseData) {
        if (responseData == null) {
            return false;
        }
        String normalized = responseData.stripLeading();
        // MethodToolCallback serializes String return values as JSON strings, while other callbacks may return plain text.
        return normalized.startsWith("ERROR:") || normalized.startsWith("\"ERROR:");
    }

    /** Whether the provider reports this completion was cut off by the output token limit (OpenAI-style finish reason "length"). */
    static boolean isTruncatedByTokenLimit(ChatResponse response) {
        if (response.getResult() == null) {
            return false;
        }
        return "length".equalsIgnoreCase(response.getResult().getMetadata().getFinishReason());
    }

    /** The first rejected tool result of the latest batch, bounded, as evidence for a stop cause. */
    static String lastRejection(List<Message> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i) instanceof ToolResponseMessage toolResponse) {
                return toolResponse.getResponses().stream().filter(response -> isErrorResponse(response.responseData())).findFirst()
                        .map(response -> response.name() + ": " + excerpt(response.responseData().strip())).orElse("not recorded");
            }
        }
        return "not recorded";
    }

    private static String excerpt(String text) {
        return text.length() <= MAX_REPEATED_REJECTION_EXCERPT_CHARS ? text : text.substring(0, MAX_REPEATED_REJECTION_EXCERPT_CHARS) + "…";
    }
}
