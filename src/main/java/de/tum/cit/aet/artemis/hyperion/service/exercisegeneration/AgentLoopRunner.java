package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * Drives the user-controlled Spring AI tool-calling loop for agentic exercise generation: the runner repeatedly calls the model, executes the requested tools, feeds the results
 * back, and continues until the model stops requesting tools, the turn budget is reached, cancellation is requested, or an error occurs. A manual loop is required because Spring
 * AI's automatic tool execution has no iteration cap and no per-step hook, so it cannot enforce the safety budget or produce the transcript. Artifact correctness is decided
 * separately by the out-of-band verifier. Task-agnostic (depends only on Spring AI), so it is wired as a bean by {@code HyperionAsyncConfiguration}.
 */
public class AgentLoopRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopRunner.class);

    /** After this many consecutive tool-execution failures the model is considered stuck and the loop ends with an error. */
    private static final int MAX_CONSECUTIVE_TOOL_FAILURES = 5;

    /** The tool the agent calls to declare the exercise complete; calling it ends the loop and hands off to the out-of-band verifier. */
    private static final String SUBMIT_TOOL_NAME = "submit";

    // --- Context-window management ---

    /** Headroom reserved below the context window for the model's response plus estimation slack; compaction triggers once the estimated prompt would exceed it. */
    private static final int RESERVE_TOKENS = 16_384;

    /** Target size of the verbatim recent tail kept across a compaction (everything older is summarized). */
    private static final int KEEP_RECENT_TOKENS = 20_000;

    /** Chars-per-token divisor for the fallback estimate; dense code/JSON tokenizes to ~3 chars/token, so 3 (not 4) avoids under-counting. */
    private static final int CHARS_PER_TOKEN = 3;

    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    private static final int TOOLCALL_OVERHEAD_TOKENS = 8;

    /** Hard cap on a single tool result kept in the live context; head and tail (where the signal lives) are kept and the middle is elided. */
    private static final int MAX_TOOL_RESPONSE_CHARS = 12_000;

    /** Per-tool-result truncation applied when serializing older messages as input to the summarizer. */
    private static final int SUMMARY_INPUT_TRUNCATE_CHARS = 2_000;

    /** Output cap for the summary, so the summary itself never becomes a context problem on the next turn. */
    private static final int SUMMARY_MAX_OUTPUT_TOKENS = 2_000;

    /** Prefix marking the synthetic compaction-summary message, so a later compaction recognizes and folds it into the next summary. */
    private static final String SUMMARY_SENTINEL = "[SESSION SUMMARY — earlier steps were compacted to fit the context window. The workspace files on disk are the source of truth; re-read any file you need.]";

    /** System prompt for the out-of-band summarization call that performs compaction; structured so the agent keeps goal, decisions, file state, and next steps. */
    private static final String SUMMARIZATION_SYSTEM_PROMPT = """
            You are compacting the working memory of an autonomous agent that is authoring a programming exercise inside a sandbox. Summarize the earlier part of the agent's \
            session so it can continue WITHOUT the full history. Be concise and strictly factual; never invent progress. Preserve exactly what the agent needs to finish: the \
            goal, hard constraints, what has already been done, key decisions and why, the current state of the workspace files, and what remains. Use this structure with short \
            bullet points:

            ## Goal
            ## Constraints
            ## Progress so far
            ## Key decisions
            ## Workspace files (paths created/edited and their purpose)
            ## Next steps

            The workspace files on disk are the source of truth — the agent can always re-read any file. Keep the whole summary under ~400 words.""";

    /** Model-call attempts before giving up: LLM endpoints have transient errors and a fresh sample usually succeeds, so one blip should not abort a generation. */
    private static final int MODEL_CALL_ATTEMPTS = 6;

    /** Exponential-backoff base/cap (ms) between model-call retries; instance fields so a test can shrink them to assert retry behaviour without real waits. */
    private long modelCallRetryBaseMillis = 1_500L;

    private long modelCallRetryCapMillis = 20_000L;

    @Nullable
    private final ChatModel chatModel;

    private final ToolCallingManager toolCallingManager;

    /** The model's usable context window in tokens; compaction keeps the conversation below {@code contextWindow - RESERVE_TOKENS}. Configurable because deployments cap it. */
    private final int contextWindowTokens;

    /**
     * Injects the collection (not a single {@link ChatModel}) and uses the first available one: multiple beans may be on the classpath, so a single-bean injection would be
     * ambiguous and fail startup.
     *
     * @param chatModels          all available chat models (may be empty if no AI provider is configured)
     * @param contextWindowTokens the model's usable context window in tokens (override per deployment)
     */
    public AgentLoopRunner(Collection<ChatModel> chatModels, int contextWindowTokens) {
        this.chatModel = chatModels.isEmpty() ? null : chatModels.iterator().next();
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.contextWindowTokens = contextWindowTokens;
    }

    /**
     * Drives the agent loop for one generation session.
     *
     * @param systemPrompt the system prompt describing the task and the available tools
     * @param userPrompt   the initial user instruction
     * @param tools        the tools object whose {@code @Tool} methods are exposed to the model (typically {@link SandboxAgentTools})
     * @param maxTurns     the hard cap on model turns (safety budget)
     * @param cancelled    a supplier polled before each turn; if it returns {@code true} the loop stops cooperatively
     * @param usageSink    invoked after every successful model call (the main loop call and the summarization call) with its {@link ChatResponse}, so the caller can record token
     *                         usage; may be {@code null}
     * @param stepListener invoked after every step with a short human-readable progress line (tool calls, completion); may be {@code null}
     * @return the loop outcome
     */
    public AgentLoopResult run(String systemPrompt, String userPrompt, Object tools, int maxTurns, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink,
            @Nullable Consumer<String> stepListener) {
        if (chatModel == null) {
            throw new IllegalStateException("No ChatModel is configured. Agentic generation is unavailable.");
        }
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(tools).build();
        ToolCallback[] toolCallbacks = provider.getToolCallbacks();

        // Spring AI 2.0 never auto-executes tools, so the response carries raw tool calls that this loop executes explicitly via toolCallingManager.executeToolCalls(...).
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks).build();

        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(systemPrompt));
        conversation.add(new UserMessage(userPrompt));

        Prompt prompt = new Prompt(conversation, options);
        String lastAssistantText = "";
        int consecutiveToolFailures = 0;
        // Context-window accounting: the previous call's real prompt-token count anchors the estimate; only messages appended since are estimated — see estimateContextTokens().
        long lastPromptTokens = 0;
        int messagesAtLastCall = 0;

        for (int turn = 1; turn <= maxTurns; turn++) {
            if (cancelled.getAsBoolean()) {
                emit(stepListener, "Cancelled before turn " + turn);
                return new AgentLoopResult(AgentLoopResult.Status.CANCELLED, turn - 1, lastAssistantText);
            }

            messagesAtLastCall = conversation.size();
            ChatResponse response = callModelWithRetries(prompt, turn, stepListener);
            if (response == null) {
                return new AgentLoopResult(AgentLoopResult.Status.ERROR, turn, lastAssistantText);
            }
            // Strip leaked harmony control tokens from tool names before dispatch (see normalizeToolNames).
            response = normalizeToolNames(response);
            emitUsage(usageSink, response);
            lastPromptTokens = promptTokensOf(response);

            String assistantText = extractText(response);
            if (assistantText != null && !assistantText.isBlank()) {
                lastAssistantText = assistantText;
            }

            if (!response.hasToolCalls()) {
                // No more tool calls: the model considers the task complete; the verifier decides whether it actually is.
                emit(stepListener, "Agent finished after " + turn + " turn(s)");
                return new AgentLoopResult(AgentLoopResult.Status.COMPLETED, turn, lastAssistantText);
            }

            List<AssistantMessage.ToolCall> toolCalls = response.getResult() != null && response.getResult().getOutput() != null ? response.getResult().getOutput().getToolCalls()
                    : List.of();
            // Transcript: one line per tool call (parsed by the client, see describeToolCall).
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                emit(stepListener, "Turn " + turn + ": " + describeToolCall(toolCall));
            }
            boolean submitRequested = toolCalls.stream().anyMatch(toolCall -> SUBMIT_TOOL_NAME.equals(toolCall.name()));

            ToolExecutionResult toolExecutionResult;
            try {
                toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
                consecutiveToolFailures = 0;
            }
            catch (RuntimeException e) {
                // Unknown tool / malformed arguments surface here: feed the error back so the model can self-correct, only giving up after MAX_CONSECUTIVE_TOOL_FAILURES.
                consecutiveToolFailures++;
                log.warn("Agent loop tool execution failed on turn {} (consecutive failures: {}): {}", turn, consecutiveToolFailures, e.getMessage());
                emit(stepListener, "Tool call could not be executed (" + e.getMessage() + "); asking the agent to correct it.");
                if (consecutiveToolFailures >= MAX_CONSECUTIVE_TOOL_FAILURES) {
                    return new AgentLoopResult(AgentLoopResult.Status.ERROR, turn, lastAssistantText);
                }
                AssistantMessage failedTurn = response.getResult().getOutput();
                conversation.add(failedTurn);
                // Must answer EVERY requested call id, or the chat-completions tool-pairing contract is violated; per-call errors also tell the model which call failed.
                List<ToolResponseMessage.ToolResponse> errorResponses = failedTurn.getToolCalls().stream()
                        .map(toolCall -> new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(),
                                "ERROR: this tool call could not be executed: " + e.getMessage()
                                        + ". Only use the available tools (read_file, write_file, edit_file, bash, verify, submit) with valid JSON arguments, then continue."))
                        .toList();
                // The catch is only reachable when this turn had tool calls (the no-tool-calls path returns above), so errorResponses always covers at least one call id.
                conversation.add(ToolResponseMessage.builder().responses(errorResponses).build());
                conversation = compactIfNeeded(conversation, lastPromptTokens, messagesAtLastCall, usageSink, stepListener);
                prompt = new Prompt(conversation, options);
                continue;
            }

            if (submitRequested) {
                // End the loop so the out-of-band verifier (which does not trust the agent) decides acceptance.
                emit(stepListener, "Agent submitted after " + turn + " turn(s)");
                return new AgentLoopResult(AgentLoopResult.Status.COMPLETED, turn, lastAssistantText);
            }

            conversation = new ArrayList<>(toolExecutionResult.conversationHistory());
            // Bound each result as it enters the context, so one oversized build log cannot blow the window before compaction runs.
            capToolResponses(conversation);
            // Budget-pressure nudge, appended AFTER the conversation is rebuilt from the tool-execution history (otherwise it would be discarded with that rebuild).
            if (turn == maxTurns - 1) {
                conversation.add(new UserMessage("You are close to the step limit. Finish the current change, make sure the build and tests reflect the intended outcome, "
                        + "and then stop calling tools."));
            }
            conversation = compactIfNeeded(conversation, lastPromptTokens, messagesAtLastCall, usageSink, stepListener);
            prompt = new Prompt(conversation, options);
        }

        emit(stepListener, "Reached the step budget of " + maxTurns + " turns");
        return new AgentLoopResult(AgentLoopResult.Status.BUDGET_EXHAUSTED, maxTurns, lastAssistantText);
    }

    /** Matches a harmony / channel control token such as {@code <|channel|>} or {@code <|end|>}. */
    private static final Pattern HARMONY_CONTROL_TOKEN = Pattern.compile("<\\|[^|]*\\|>");

    /**
     * Removes leaked harmony control tokens from tool-call names, so a name like {@code bash<|channel|>commentary} dispatches as {@code bash}. Rebuilds the response only when a
     * name actually changes (usually a no-op).
     *
     * @param response the model response (possibly carrying a leaked tool name)
     * @return the same response, or a copy with normalized tool-call names
     */
    private static ChatResponse normalizeToolNames(ChatResponse response) {
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
                log.warn("Normalized leaked tool name '{}' to '{}'", toolCall.name(), sanitized);
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

    /**
     * Strips harmony control tokens ({@code <|...|>}) and the trailing channel suffix from a tool name, reducing e.g. {@code bash<|channel|>commentary} to {@code bash}. A clean
     * name is returned unchanged.
     *
     * @param name the raw tool name from the model
     * @return the normalized tool name
     */
    static String sanitizeToolName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (!name.contains("<|")) {
            return name.strip();
        }
        // Everything before the first control token is the real tool name; the rest is leakage.
        String leading = name.substring(0, name.indexOf("<|"));
        return HARMONY_CONTROL_TOKEN.matcher(leading).replaceAll("").strip();
    }

    /**
     * Renders a tool call as a single transcript line: tool name plus its most informative argument (path for file tools, command for bash), truncated to keep large bodies out.
     * <p>
     * Cross-cutting contract: the client ({@code generation-progress.model.ts}) parses this line for the "files changed" view, so tool names must stay stable and for file tools
     * the {@code path} must be rendered FIRST and IN FULL — otherwise a large {@code content} argument would push it past the truncation point and the UI would miss the file.
     */
    private static String describeToolCall(AssistantMessage.ToolCall toolCall) {
        String arguments = toolCall.arguments() == null ? "" : toolCall.arguments().replaceAll("\\s+", " ").trim();
        String path = extractJsonStringValue(arguments, "path");
        if (path != null) {
            return toolCall.name() + " " + path;
        }
        if (arguments.length() > 160) {
            arguments = arguments.substring(0, 160) + "…";
        }
        return toolCall.name() + " " + arguments;
    }

    /**
     * Extracts a JSON string value (e.g. the {@code path} argument of a file tool) from a tool call's raw JSON arguments, unescaping the common escapes, or {@code null} if absent.
     */
    @Nullable
    private static String extractJsonStringValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");
    }

    /**
     * Test hook: shrink the model-call retry backoff (base and cap, in milliseconds) so retry behaviour can be asserted deterministically without real waits.
     *
     * @param baseMillis the backoff base
     * @param capMillis  the backoff cap
     */
    void setModelCallRetryTimingForTests(long baseMillis, long capMillis) {
        this.modelCallRetryBaseMillis = baseMillis;
        this.modelCallRetryCapMillis = capMillis;
    }

    /**
     * Calls the model, retrying a transient failure a few times (with exponential backoff) before giving up. Returns {@code null} only when every attempt failed, which the caller
     * turns into an ERROR outcome.
     */
    @Nullable
    private ChatResponse callModelWithRetries(Prompt prompt, int turn, @Nullable Consumer<String> stepListener) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MODEL_CALL_ATTEMPTS; attempt++) {
            try {
                return chatModel.call(prompt);
            }
            catch (RuntimeException e) {
                lastError = e;
                log.warn("Agent loop model call failed on turn {} (attempt {}/{}): {}", turn, attempt, MODEL_CALL_ATTEMPTS, e.getMessage());
                if (attempt < MODEL_CALL_ATTEMPTS) {
                    emit(stepListener, "Model call failed (" + e.getMessage() + "); retrying.");
                    // Exponential backoff with jitter so retries spread across time instead of re-hitting the same failure burst.
                    long backoff = Math.min(modelCallRetryCapMillis, modelCallRetryBaseMillis * (1L << (attempt - 1)));
                    if (backoff > 0) {
                        backoff += ThreadLocalRandom.current().nextLong(modelCallRetryBaseMillis + 1);
                        try {
                            Thread.sleep(backoff);
                        }
                        catch (InterruptedException ie) {
                            // Honour the interrupt instead of swallowing it: stop retrying and let the caller turn the null into an ERROR outcome.
                            Thread.currentThread().interrupt();
                            log.warn("Interrupted while backing off before a model-call retry on turn {}", turn);
                            return null;
                        }
                    }
                }
            }
        }
        log.error("Agent loop model call failed on turn {} after {} attempts", turn, MODEL_CALL_ATTEMPTS, lastError);
        emit(stepListener, "Model call failed after " + MODEL_CALL_ATTEMPTS + " attempts: " + (lastError == null ? "unknown error" : lastError.getMessage()));
        return null;
    }

    /**
     * Compacts the conversation when the estimated prompt approaches the context window, returning the original list otherwise. The trigger fires once the estimated token count
     * exceeds {@code contextWindow - RESERVE_TOKENS}.
     *
     * @param conversation       the current conversation (system, initial user, then assistant/tool-result turns)
     * @param lastPromptTokens   the real prompt-token count the previous model call reported (0 if unavailable yet)
     * @param messagesAtLastCall the conversation size when that call was issued, so only the messages appended since are estimated
     * @param usageSink          receives the summarization call's {@link ChatResponse} for token-usage tracking
     * @param stepListener       progress sink, notified when compaction runs
     * @return the (possibly compacted) conversation
     */
    private List<Message> compactIfNeeded(List<Message> conversation, long lastPromptTokens, int messagesAtLastCall, @Nullable Consumer<ChatResponse> usageSink,
            @Nullable Consumer<String> stepListener) {
        long contextTokens = estimateContextTokens(conversation, lastPromptTokens, messagesAtLastCall);
        if (contextTokens <= (long) contextWindowTokens - RESERVE_TOKENS) {
            return conversation;
        }
        emit(stepListener, "Context window under pressure (~" + contextTokens + " tokens); compacting earlier steps.");
        return compact(conversation, usageSink);
    }

    /**
     * Estimates the prompt's token count: anchors on the provider's real {@code promptTokens} from the previous call (which also captures out-of-band tool-schema tokens) and adds
     * a {@code chars/CHARS_PER_TOKEN} estimate of only the messages appended since. Before the first call the whole conversation is estimated.
     */
    static long estimateContextTokens(List<Message> conversation, long lastPromptTokens, int messagesAtLastCall) {
        if (lastPromptTokens <= 0 || messagesAtLastCall < 0 || messagesAtLastCall > conversation.size()) {
            return estimateTokens(conversation, 0, conversation.size());
        }
        return lastPromptTokens + estimateTokens(conversation, messagesAtLastCall, conversation.size());
    }

    private static long estimateTokens(List<Message> conversation, int from, int to) {
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
                tokens += TOOLCALL_OVERHEAD_TOKENS + tokensForChars(length(response.responseData()));
            }
            return tokens;
        }
        if (message instanceof AssistantMessage assistant) {
            tokens += tokensForChars(length(assistant.getText()));
            for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                tokens += TOOLCALL_OVERHEAD_TOKENS + tokensForChars(length(toolCall.name()) + length(toolCall.arguments()));
            }
            return tokens;
        }
        return tokens + tokensForChars(length(message.getText()));
    }

    private static long tokensForChars(int chars) {
        return (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    private static int length(@Nullable String value) {
        return value == null ? 0 : value.length();
    }

    /** The real prompt-token count the response reports, or 0 if the provider did not supply usage. */
    private static long promptTokensOf(@Nullable ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return 0;
        }
        Number promptTokens = response.getMetadata().getUsage().getPromptTokens();
        return promptTokens == null ? 0 : promptTokens.longValue();
    }

    /** Truncates any tool result longer than {@link #MAX_TOOL_RESPONSE_CHARS}, keeping head and tail (where the signal lives) and eliding the middle. Mutates the list in place. */
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
        // head + marker + tail stays within MAX_TOOL_RESPONSE_CHARS.
        int head = MAX_TOOL_RESPONSE_CHARS / 4;
        int elidedEstimate = data.length() - MAX_TOOL_RESPONSE_CHARS;
        String marker = "\n[… " + elidedEstimate + " characters elided to fit the context window …]\n";
        int tail = Math.max(0, MAX_TOOL_RESPONSE_CHARS - head - marker.length());
        return data.substring(0, head) + marker + data.substring(data.length() - tail);
    }

    /**
     * Compacts the conversation: keeps the protected prefix (system prompt + initial instruction), summarizes the oldest turns into one synthetic {@link UserMessage} marked with
     * {@link #SUMMARY_SENTINEL} (so a later compaction folds it forward), and keeps the newest turns verbatim. The cut lands on a turn boundary so the result satisfies the
     * tool-pairing contract. If summarization fails, the old region is dropped behind a marker rather than aborting the run — the workspace files remain the source of truth.
     */
    List<Message> compact(List<Message> conversation, @Nullable Consumer<ChatResponse> usageSink) {
        int protectedPrefix = Math.min(2, conversation.size());
        if (conversation.size() <= protectedPrefix + 1) {
            // Only the system prompt, the initial instruction, and at most one turn — nothing older to summarize.
            return conversation;
        }
        int cut = findCutIndex(conversation, protectedPrefix);
        if (cut <= protectedPrefix) {
            // Nothing older than the kept tail to summarize; per-result caps already bound individual messages, so leave it as-is.
            return conversation;
        }
        List<Message> toSummarize = conversation.subList(protectedPrefix, cut);
        String summaryBody;
        try {
            summaryBody = summarize(toSummarize, usageSink);
        }
        catch (RuntimeException e) {
            log.warn("Compaction summarization failed ({}); dropping {} older message(s) behind a marker instead.", e.getMessage(), toSummarize.size());
            summaryBody = "[" + toSummarize.size()
                    + " earlier messages were omitted to fit the context window. Re-read any workspace file you need with read_file or `ls -R`/`cat` via bash.]";
        }
        List<Message> rebuilt = new ArrayList<>(protectedPrefix + 1 + (conversation.size() - cut));
        for (int i = 0; i < protectedPrefix; i++) {
            rebuilt.add(conversation.get(i));
        }
        rebuilt.add(new UserMessage(SUMMARY_SENTINEL + "\n\n" + summaryBody));
        rebuilt.addAll(conversation.subList(cut, conversation.size()));
        assertValidPairing(rebuilt);
        return rebuilt;
    }

    /**
     * Finds the index at which the kept verbatim tail begins: walks backward accumulating ~{@link #KEEP_RECENT_TOKENS} of recent messages, snaps forward to a turn start (so the
     * tail never begins with an orphaned tool result), then advances turn by turn until the tail fits the budget once the summary is added back. {@code KEEP_RECENT_TOKENS} is a
     * target, not a floor — the real floor is "the tail must fit".
     */
    int findCutIndex(List<Message> conversation, int protectedPrefix) {
        int n = conversation.size();
        int cut = n;
        long tail = 0;
        for (int i = n - 1; i >= protectedPrefix; i--) {
            long messageTokens = estimateMessageTokens(conversation.get(i));
            if (tail > 0 && tail + messageTokens > KEEP_RECENT_TOKENS) {
                break;
            }
            tail += messageTokens;
            cut = i;
        }
        cut = snapToTurnStart(conversation, cut);
        // Push the cut forward until prefix + summary + kept tail all fit under the budget.
        long budget = (long) contextWindowTokens - RESERVE_TOKENS;
        long fixed = estimateTokens(conversation, 0, protectedPrefix) + SUMMARY_MAX_OUTPUT_TOKENS + MESSAGE_OVERHEAD_TOKENS;
        while (cut < n && fixed + estimateTokens(conversation, cut, n) > budget) {
            cut = snapToTurnStart(conversation, cut + 1);
        }
        if (cut == n) {
            // Even the minimal tail does not fit: the conversation becomes summary-only. Rare (per-result caps bound messages), so make it observable.
            log.warn("Compaction kept no recent turns verbatim: the context did not fit even minimally (window {} tokens, {} messages).", contextWindowTokens, n);
        }
        return cut;
    }

    /** Advances {@code index} forward past any tool-result messages so it lands on a turn start (system/user/assistant), which is a safe place to begin the kept tail. */
    private static int snapToTurnStart(List<Message> conversation, int index) {
        int cut = index;
        while (cut < conversation.size() && conversation.get(cut) instanceof ToolResponseMessage) {
            cut++;
        }
        return cut;
    }

    /** Summarizes the given older messages via a tool-free model call, producing the structured summary that replaces them. */
    private String summarize(List<Message> messages, @Nullable Consumer<ChatResponse> usageSink) {
        StringBuilder transcript = new StringBuilder();
        for (Message message : messages) {
            transcript.append(renderForSummary(message)).append('\n');
        }
        List<Message> summaryPrompt = List.of(new SystemMessage(SUMMARIZATION_SYSTEM_PROMPT),
                new UserMessage("Summarize the following earlier session messages into the structured summary described above:\n\n" + transcript));
        // Plain ChatOptions (no tool callbacks) so the summarizer cannot call tools; the output cap keeps the summary small.
        ChatResponse response = chatModel.call(new Prompt(summaryPrompt, ChatOptions.builder().maxTokens(SUMMARY_MAX_OUTPUT_TOKENS).build()));
        emitUsage(usageSink, response);
        String text = extractText(response);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("summarizer returned an empty summary");
        }
        return text.strip();
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

    /**
     * Asserts the tool-pairing contract on a rebuilt conversation (each tool-result preceded by an assistant tool-call turn and vice versa), turning a compaction bug into a
     * catchable internal error rather than a provider 400.
     */
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

    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private static void emit(@Nullable Consumer<String> stepListener, String message) {
        if (stepListener != null) {
            stepListener.accept(message);
        }
    }

    private static void emitUsage(@Nullable Consumer<ChatResponse> usageSink, @Nullable ChatResponse response) {
        if (usageSink != null && response != null) {
            usageSink.accept(response);
        }
    }
}
