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
 * Runs the user-controlled Spring AI tool-calling loop that powers agentic exercise generation. Spring AI's automatic tool execution has no maximum-iteration cap and exposes no
 * per-step hook, so this runner drives the loop manually: tool execution is disabled on the options, and the runner repeatedly calls the model, executes the requested tools,
 * feeds the results back, and continues — bounded by an explicit turn budget and a cooperative cancellation check — which is what makes a transcript and a hard safety budget
 * possible. The loop terminates when the model stops requesting tools, the budget is reached, cancellation is requested, or on error; the produced artifact's correctness is
 * decided separately by the out-of-band verifier. The class is deliberately task-agnostic (it depends only on Spring AI), so it is wired as a bean by
 * {@code HyperionAsyncConfiguration} rather than annotated as a {@code @Service}.
 */
public class AgentLoopRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopRunner.class);

    /** After this many consecutive tool-execution failures the model is considered stuck and the loop ends with an error. */
    private static final int MAX_CONSECUTIVE_TOOL_FAILURES = 5;

    /** The tool the agent calls to declare the exercise complete; calling it ends the loop and hands off to the out-of-band verifier. */
    private static final String SUBMIT_TOOL_NAME = "submit";

    // --- Context-window management. Compaction strategy adapted from the pi coding agent. ---

    /** Headroom reserved below the context window for the model's response plus estimation slack; compaction triggers once the estimated prompt would exceed it. */
    private static final int RESERVE_TOKENS = 16_384;

    /** Target size, in tokens, of the verbatim recent tail kept across a compaction (everything older is summarized). */
    private static final int KEEP_RECENT_TOKENS = 20_000;

    /** Chars-per-token divisor for the fallback estimate; dense code/JSON tokenizes to ~3 chars/token, so 3 (not 4) avoids under-counting. */
    private static final int CHARS_PER_TOKEN = 3;

    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    private static final int TOOLCALL_OVERHEAD_TOKENS = 8;

    /** Hard cap on the characters of a single tool result kept in the live context; the head and tail (where the signal lives) are kept and the middle is elided. */
    private static final int MAX_TOOL_RESPONSE_CHARS = 12_000;

    /** When serializing older messages as input to the summarizer, each tool result is truncated to this many characters. */
    private static final int SUMMARY_INPUT_TRUNCATE_CHARS = 2_000;

    /** Upper bound on the summary the compactor asks the model to produce, so the summary itself never becomes a context problem on the next turn. */
    private static final int SUMMARY_MAX_OUTPUT_TOKENS = 2_000;

    /** Prefix marking the synthetic message that carries the compaction summary, so a later compaction can recognize and fold it into the next summary (iterative compaction). */
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

    /**
     * How many times to issue a single model call before giving up. LLM endpoints have transient errors (rate limits, 5xx/401 session blips, occasional malformed tool-format
     * responses); since the model is sampled fresh each call, a retry usually succeeds, so one transient failure should not abort a whole generation. A flaky endpoint can fail
     * several consecutive calls, so we retry generously AND back off (below) so the retries spread across time instead of hammering the same failure spike.
     */
    private static final int MODEL_CALL_ATTEMPTS = 6;

    /**
     * Exponential-backoff base and cap (milliseconds) between model-call retries, plus jitter, so the retries spread across time instead of hammering the same failure spike.
     * Instance fields (not constants) so a test can shrink them to assert retry behaviour without real waits.
     */
    private long modelCallRetryBaseMillis = 1_500L;

    private long modelCallRetryCapMillis = 20_000L;

    @Nullable
    private final ChatModel chatModel;

    private final ToolCallingManager toolCallingManager;

    /**
     * The model's usable context window in tokens; compaction keeps the conversation below {@code contextWindow - RESERVE_TOKENS}. Configurable because deployments cap it well
     * below the model's theoretical maximum (the gpt-oss-120b endpoint serves 65536, not 200k).
     */
    private final int contextWindowTokens;

    /**
     * Multiple {@link ChatModel} beans may be present (e.g. both the Azure OpenAI and OpenAI starters are on the classpath), so we inject the collection and use the first
     * available one — the same strategy {@code SpringAIConfiguration} uses to build the shared {@code ChatClient}. Injecting a single {@code ChatModel} here would be ambiguous
     * and fail context startup.
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

        // Spring AI 2.0 removed the in-model tool-execution loop (and the internalToolExecutionEnabled flag): ChatModel never auto-executes tools, so the response carries the raw
        // tool calls and our loop executes them explicitly via toolCallingManager.executeToolCalls(...) below — the user-controlled DefaultToolCallingManager pattern.
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks).build();

        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(systemPrompt));
        conversation.add(new UserMessage(userPrompt));

        Prompt prompt = new Prompt(conversation, options);
        String lastAssistantText = "";
        int consecutiveToolFailures = 0;
        // Context-window accounting: the real prompt-token count reported by the previous call anchors the estimate, and the conversation size at that call lets us add a
        // conservative estimate of only what has been appended since (the new assistant turn + tool results) — see estimateContextTokens().
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
                // The model has no more tool calls — it considers the task complete. The verifier decides whether it actually is.
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
                // A real model occasionally requests an unknown tool or emits malformed arguments, which the tool-calling manager surfaces as an exception. Treat this as a
                // recoverable observation — feed the error back so the model can correct itself on the next turn — rather than aborting the whole generation. Only give up after
                // several consecutive failures, which indicates the model is stuck.
                consecutiveToolFailures++;
                log.warn("Agent loop tool execution failed on turn {} (consecutive failures: {}): {}", turn, consecutiveToolFailures, e.getMessage());
                emit(stepListener, "Tool call could not be executed (" + e.getMessage() + "); asking the agent to correct it.");
                if (consecutiveToolFailures >= MAX_CONSECUTIVE_TOOL_FAILURES) {
                    return new AgentLoopResult(AgentLoopResult.Status.ERROR, turn, lastAssistantText);
                }
                AssistantMessage failedTurn = response.getResult().getOutput();
                conversation.add(failedTurn);
                // Answer EVERY requested tool call with an error, rather than appending a bare user nudge: an assistant message carrying tool calls must be followed by tool
                // results that cover each call id, or the chat-completions tool-pairing contract is violated. Feeding the error back per call also tells the model exactly which
                // call failed.
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
                // The agent declared the work complete; end the loop so the out-of-band verifier (which does not trust the agent) decides acceptance.
                emit(stepListener, "Agent submitted after " + turn + " turn(s)");
                return new AgentLoopResult(AgentLoopResult.Status.COMPLETED, turn, lastAssistantText);
            }

            conversation = new ArrayList<>(toolExecutionResult.conversationHistory());
            // Bound each tool result the moment it enters the context, so a single oversized build log or file dump cannot blow the window before compaction even gets a chance.
            capToolResponses(conversation);
            // Inject a budget-pressure nudge shortly before the cap so the model converges instead of being cut off mid-edit. It must be appended AFTER the conversation has
            // been rebuilt from the tool-execution history, otherwise it would be discarded when that history replaces the list.
            if (turn == maxTurns - 1) {
                conversation.add(new UserMessage("You are close to the step limit. Finish the current change, make sure the build and tests reflect the intended outcome, "
                        + "and then stop calling tools."));
            }
            // Compaction valve: once the estimated prompt approaches the context window, summarize the oldest turns into a compact summary and keep the recent ones verbatim, so
            // a long build/edit loop never overflows the window.
            conversation = compactIfNeeded(conversation, lastPromptTokens, messagesAtLastCall, usageSink, stepListener);
            prompt = new Prompt(conversation, options);
        }

        emit(stepListener, "Reached the step budget of " + maxTurns + " turns");
        return new AgentLoopResult(AgentLoopResult.Status.BUDGET_EXHAUSTED, maxTurns, lastAssistantText);
    }

    /** Matches a harmony / channel control token such as {@code <|channel|>} or {@code <|end|>}, used to strip them from a leaked tool name. */
    private static final Pattern HARMONY_CONTROL_TOKEN = Pattern.compile("<\\|[^|]*\\|>");

    /**
     * Returns a response whose tool-call names have any leaked harmony control tokens removed, so a name like {@code bash<|channel|>commentary} dispatches as {@code bash}. The
     * response is rebuilt only when a name actually changes (the common case is a no-op), preserving the assistant text, metadata, and every tool call's id and arguments.
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
     * Strips harmony / channel control tokens ({@code <|...|>}) and any trailing channel suffix from a tool name. A leaked {@code bash<|channel|>commentary} (or
     * {@code bash<|channel|>}) reduces to {@code bash}: control tokens are removed and, once a control token has been seen, any remaining trailing word (the channel name) is
     * dropped, leaving the leading real tool name. A clean name is returned unchanged.
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
        // Everything before the first control token is the real tool name; the control token and whatever channel noise follows it are leakage.
        String leading = name.substring(0, name.indexOf("<|"));
        return HARMONY_CONTROL_TOKEN.matcher(leading).replaceAll("").strip();
    }

    /**
     * Renders a tool call as a compact, single-line description for the transcript: the tool name followed by the most informative argument (the path for file tools, the command
     * for bash), truncated so a large file body or command never floods the transcript.
     * <p>
     * The line format is parsed on the client ({@code generation-progress.model.ts}) to derive the "files changed" view, so two properties must hold: the tool names stay stable,
     * and for file tools the {@code path} is rendered FIRST and IN FULL. Otherwise a large {@code content} argument (or an argument order that puts {@code content} before
     * {@code path}) would push the path past the truncation point and the UI would silently miss the file. Rendering just the path also keeps a file body out of the transcript.
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
                    // Exponential backoff with jitter so retries spread across time and catch a flaky endpoint's healthy windows, rather than re-hitting the same failure burst.
                    long backoff = Math.min(modelCallRetryCapMillis, modelCallRetryBaseMillis * (1L << (attempt - 1)));
                    if (backoff > 0) {
                        backoff += ThreadLocalRandom.current().nextLong(modelCallRetryBaseMillis + 1);
                        try {
                            Thread.sleep(backoff);
                        }
                        catch (InterruptedException ie) {
                            // Honour cooperative cancellation/interrupt instead of swallowing it: stop retrying and let the caller turn the null into an ERROR outcome.
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
     * Estimates the prompt's token count. The authoritative number is the provider's real {@code promptTokens} from the previous call (which also captures tool-schema tokens
     * sent out of band); only the messages appended since that call are estimated with the conservative {@code chars/CHARS_PER_TOKEN} fallback. Before the first call (no usage
     * yet) the whole conversation is estimated.
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

    /**
     * Truncates the body of any tool result longer than {@link #MAX_TOOL_RESPONSE_CHARS}, keeping the head and tail (where the signal — compiler errors, failing assertions, the
     * verify.sh result line — lives) and eliding the middle. Mutates the list in place.
     */
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
        // Invariant: head + marker + tail stays within MAX_TOOL_RESPONSE_CHARS, with the elided count computed from the actual kept head + tail.
        int head = MAX_TOOL_RESPONSE_CHARS / 4;
        int elidedEstimate = data.length() - MAX_TOOL_RESPONSE_CHARS;
        String marker = "\n[… " + elidedEstimate + " characters elided to fit the context window …]\n";
        int tail = Math.max(0, MAX_TOOL_RESPONSE_CHARS - head - marker.length());
        return data.substring(0, head) + marker + data.substring(data.length() - tail);
    }

    /**
     * Performs the compaction: keeps the system prompt and the initial instruction, summarizes the oldest turns into one structured summary message, and keeps the newest turns
     * verbatim. The cut between summarized and kept always lands on a turn boundary (never between an assistant tool-call and its tool-results), so the rebuilt conversation
     * satisfies the chat-completions tool-pairing contract. The summary is carried in a synthetic {@link UserMessage}, which a later compaction recognizes by
     * {@link #SUMMARY_SENTINEL} and folds into the next summary. If the summarization call itself fails, the old region is dropped behind a marker rather than aborting the run —
     * the workspace files remain the source of truth.
     */
    List<Message> compact(List<Message> conversation, @Nullable Consumer<ChatResponse> usageSink) {
        int protectedPrefix = Math.min(2, conversation.size());
        if (conversation.size() <= protectedPrefix + 1) {
            // Only the system prompt, the initial instruction, and at most one turn — nothing older to summarize.
            return conversation;
        }
        int cut = findCutIndex(conversation, protectedPrefix);
        if (cut <= protectedPrefix) {
            // The recent tail alone fills the budget; per-result caps already bound individual messages, so leave the conversation as-is rather than summarizing nothing.
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
     * Finds the index at which the kept (verbatim) tail begins. It walks backward accumulating roughly {@link #KEEP_RECENT_TOKENS} of recent messages, snaps the boundary FORWARD
     * to a turn start (any message that is not a tool-result, so the kept tail never begins with an orphaned tool result and the summarized region ends at a turn boundary), and
     * then keeps advancing — turn by turn — until the kept tail provably fits under the budget even once the summary is added back. {@code keepRecent} is therefore a target, not
     * a floor: the real floor is "the tail must fit".
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
        // Push the cut forward (always to a turn start) until the system prompt + initial instruction + the summary + the kept tail all fit under the budget.
        long budget = (long) contextWindowTokens - RESERVE_TOKENS;
        long fixed = estimateTokens(conversation, 0, protectedPrefix) + SUMMARY_MAX_OUTPUT_TOKENS + MESSAGE_OVERHEAD_TOKENS;
        while (cut < n && fixed + estimateTokens(conversation, cut, n) > budget) {
            cut = snapToTurnStart(conversation, cut + 1);
        }
        if (cut == n) {
            // Even the minimal kept tail does not fit under the budget; the conversation becomes summary-only. Per-result caps already bound individual messages, so this is rare,
            // but make it observable.
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
        // Tool-free completion: a plain ChatOptions (no tool callbacks) means the summarizer cannot call tools, and an output cap keeps the summary itself small.
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
     * Asserts the chat-completions tool-pairing contract on a (re)built conversation: every tool-result message is immediately preceded by an assistant message carrying tool
     * calls, and every assistant message carrying tool calls is immediately followed by a tool-result message. This is a cheap safety net that turns a compaction bug into a
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
