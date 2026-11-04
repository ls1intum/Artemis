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

    /**
     * The occurrence of an identical tool call that is answered with an explicit observation instead of its result: the same tool and arguments when it was rejected, or the same
     * tool, arguments and result when it succeeded. Neither can make progress by being repeated. Only a repeated rejection counts toward ending the session: a repeated
     * observation is answered with a note, and the turn budget bounds it.
     */
    static final int REPEATED_REJECTION_ESCALATION = 3;

    private static final int MAX_REPEATED_REJECTION_EXCERPT_CHARS = 400;

    /** Tools that change the workspace. Their successful result does not show whether the workspace changed in between, so identical successes are not repeats. */
    private static final Set<String> WORKSPACE_MUTATING_TOOLS = Set.of("write_file", "edit_file", "delete_file");

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
        if (toolCalls == null || toolCalls.isEmpty()) {
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
                return toolResponse.getResponses().stream().anyMatch(response -> !"verify".equals(response.name()) && isErrorResponse(response.responseData()));
            }
        }
        return false;
    }

    /**
     * Counts the calls of the latest tool batch and replaces the result of each call repeated {@link #REPEATED_REJECTION_ESCALATION} or more times with an explicit observation
     * that repeating it changes nothing. A rejected call is identified by tool and arguments; a successful call also by its result, so re-reading a file after an edit is not a
     * repeat. A model that interleaves the same call with other calls never trips the consecutive limit, so without this it can spend a whole session repeating it.
     *
     * @return how many calls of the batch were repeated rejections, which count toward ending the session
     */
    static int escalateRepeatedRejections(List<Message> conversation, List<AssistantMessage.ToolCall> toolCalls, Map<String, Integer> repeatedCallCounts) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i) instanceof ToolResponseMessage toolResponse) {
                int repeatedRejections = 0;
                boolean replaced = false;
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>(toolResponse.getResponses().size());
                for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                    if ("verify".equals(response.name()) || SUBMIT_TOOL_NAME.equals(response.name())) {
                        responses.add(response);
                        continue;
                    }
                    boolean rejected = isErrorResponse(response.responseData());
                    if (!rejected && WORKSPACE_MUTATING_TOOLS.contains(response.name())) {
                        // A write reports only what it wrote, not whether the file had changed since the last identical write, so repeating it is not evidence of a loop.
                        responses.add(response);
                        continue;
                    }
                    String arguments = toolCalls.stream().filter(call -> call.id() != null && call.id().equals(response.id())).map(AssistantMessage.ToolCall::arguments).findFirst()
                            .orElse("");
                    String key = response.name() + '\n' + arguments + (rejected ? "" : '\n' + response.responseData());
                    int occurrences = repeatedCallCounts.merge(key, 1, Integer::sum);
                    if (occurrences < REPEATED_REJECTION_ESCALATION) {
                        responses.add(response);
                        continue;
                    }
                    replaced = true;
                    if (rejected) {
                        repeatedRejections++;
                    }
                    String observation = excerpt(response.responseData().strip());
                    // A repeated observation is not a rejection: the note must not start with ERROR, or it would count toward the consecutive-failure limit.
                    String escalation = rejected
                            ? "ERROR: this exact " + response.name() + " call has now been rejected " + occurrences
                                    + " times and will be rejected every time. Do not repeat it; take a different action. Each further repeat counts toward ending this "
                                    + "session. The rejection was: " + observation
                            : "NOTE: this exact " + response.name() + " call has now returned the same result " + occurrences
                                    + " times. Repeating it cannot change anything until you change the workspace; take a different action. The unchanged result was: "
                                    + observation;
                    responses.add(new ToolResponseMessage.ToolResponse(response.id(), response.name(), escalation));
                }
                if (replaced) {
                    conversation.set(i, ToolResponseMessage.builder().responses(responses).metadata(toolResponse.getMetadata()).build());
                }
                return repeatedRejections;
            }
        }
        return 0;
    }

    static boolean hasOnlyRecoverableFileToolRejections(List<Message> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i) instanceof ToolResponseMessage toolResponse) {
                List<ToolResponseMessage.ToolResponse> rejected = toolResponse.getResponses().stream()
                        .filter(response -> !"verify".equals(response.name()) && isErrorResponse(response.responseData())).toList();
                return !rejected.isEmpty() && rejected.stream().allMatch(response -> "edit_file".equals(response.name()) || "search".equals(response.name()));
            }
        }
        return false;
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
        if (response.getResult() == null || response.getResult().getMetadata() == null) {
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
