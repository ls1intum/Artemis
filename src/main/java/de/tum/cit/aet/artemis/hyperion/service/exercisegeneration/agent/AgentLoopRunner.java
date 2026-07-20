package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.knuddels.jtokkit.api.EncodingType;

import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

/**
 * Drives the Spring AI tool-calling loop for agentic exercise generation: repeatedly calls the model, executes the requested tools, and feeds the results back until the model
 * stops, the turn budget is reached, cancellation is requested, or an error occurs. A manual loop is required because Spring AI's automatic tool execution has no iteration cap and
 * no per-step hook, so it cannot enforce the safety budget or produce the transcript. Artifact correctness is decided separately by the authoritative verifier.
 */
public class AgentLoopRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopRunner.class);

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    /** After this many consecutive tool-execution failures the model is considered stuck and the loop ends with an error. */
    private static final int MAX_CONSECUTIVE_TOOL_FAILURES = 5;

    /** The tool the agent calls to declare the exercise complete; calling it ends the loop and hands off to the authoritative verifier. */
    private static final String SUBMIT_TOOL_NAME = "submit";

    private static final int MAX_PROGRESS_PATH_CHARS = 160;

    private static final Pattern UNSAFE_PROGRESS_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

    // --- Context-window management ---

    /** Headroom reserved below the context window for the default response allowance plus estimation slack. */
    private static final int RESERVE_TOKENS = 20_480;

    private static final int CONTEXT_ESTIMATION_SAFETY_TOKENS = 4_096;

    private static final int MIN_TURN_OUTPUT_TOKENS = 1_024;

    /** Target size of the verbatim recent tail kept across a compaction (everything older is summarized). */
    private static final int KEEP_RECENT_TOKENS = 20_000;

    /**
     * Exact tokenizer for text spans. gpt-5-mini and the gpt-oss family use the {@code o200k_base} encoding, so we pin it here rather than take the library default
     * ({@code cl100k_base}). It only counts message text; the per-message structural overheads below (envelope, tool-call framing) are added on top because the framework
     * estimator does not see them.
     */
    private static final JTokkitTokenCountEstimator TEXT_TOKEN_ESTIMATOR = new JTokkitTokenCountEstimator(EncodingType.O200K_BASE);

    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    private static final int TOOLCALL_OVERHEAD_TOKENS = 8;

    /** Hard cap on a single tool result kept in the live context; head and tail (where the signal lives) are kept and the middle is elided. */
    private static final int MAX_TOOL_RESPONSE_CHARS = 12_000;

    /** Per-tool-result truncation applied when serializing older messages as input to the summarizer. */
    private static final int SUMMARY_INPUT_TRUNCATE_CHARS = 2_000;

    /** Output cap for the summary, so the summary itself never becomes a context problem on the next turn. */
    private static final int SUMMARY_MAX_OUTPUT_TOKENS = 4_096;

    /**
     * Fallback per-turn completion cap for the main agent call, bounding a single tool-calling step's cost/latency. A deployment can override it with
     * {@code spring.ai.openai.chat.options.max-tokens} or {@code spring.ai.openai.chat.options.max-completion-tokens} (see
     * {@link #configuredTurnTokenLimit()}).
     */
    private static final int TURN_MAX_OUTPUT_TOKENS = 16_384;

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

    /** One re-sample for a successful response that contains neither text nor tool calls. Transport failures use only the provider SDK's retry policy. */
    private static final int EMPTY_RESPONSE_SAMPLES = 2;

    /** Backoff base/cap (ms) before re-sampling an empty response; instance fields keep tests deterministic. */
    private long emptyResponseRetryBaseMillis = 1_500L;

    private long emptyResponseRetryCapMillis = 20_000L;

    private final Duration providerHardFailureCooldown;

    private final ProviderFailureCooldown providerFailureCooldown;

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
    public AgentLoopRunner(Collection<ChatModel> chatModels, int contextWindowTokens, Duration providerHardFailureCooldown, ProviderFailureCooldown providerFailureCooldown) {
        this.chatModel = chatModels.isEmpty() ? null : new HarmonyScrubbingChatModel(chatModels.iterator().next());
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.contextWindowTokens = contextWindowTokens;
        this.providerHardFailureCooldown = providerHardFailureCooldown;
        this.providerFailureCooldown = providerFailureCooldown;
    }

    /**
     * The model id the configured {@link ChatModel} was set up with. It is pinned on every request because Spring AI uses prompt options in place of model defaults when they are
     * present. Returns {@code null} when the model exposes no options.
     */
    @Nullable
    private String configuredModel() {
        return chatModel != null && chatModel.getOptions() != null ? chatModel.getOptions().getModel() : null;
    }

    private OpenAiChatOptions.Builder configuredOptionsBuilder() {
        var defaults = chatModel == null ? null : chatModel.getOptions();
        return defaults instanceof OpenAiChatOptions openAiDefaults ? openAiDefaults.mutate() : OpenAiChatOptions.builder();
    }

    private boolean hasConfiguredReasoningEffort() {
        var defaults = chatModel == null ? null : chatModel.getOptions();
        return defaults instanceof OpenAiChatOptions openAiDefaults && openAiDefaults.getReasoningEffort() != null;
    }

    private record TurnTokenLimit(boolean legacy, int tokens) {
    }

    private TurnTokenLimit configuredTurnTokenLimit() {
        var defaults = chatModel == null ? null : chatModel.getOptions();
        if (defaults instanceof OpenAiChatOptions openAiDefaults && openAiDefaults.getMaxCompletionTokens() != null) {
            return new TurnTokenLimit(false, openAiDefaults.getMaxCompletionTokens());
        }
        if (defaults != null && defaults.getMaxTokens() != null) {
            return new TurnTokenLimit(true, defaults.getMaxTokens());
        }
        return new TurnTokenLimit(false, TURN_MAX_OUTPUT_TOKENS);
    }

    private boolean usesLegacyMaxTokens() {
        return configuredTurnTokenLimit().legacy();
    }

    private OpenAiChatOptions agentOptions(ToolCallback[] toolCallbacks, List<Message> conversation) {
        TurnTokenLimit configuredLimit = configuredTurnTokenLimit();
        long available = (long) contextWindowTokens - estimateTokens(conversation, 0, conversation.size()) - CONTEXT_ESTIMATION_SAFETY_TOKENS;
        if (available < MIN_TURN_OUTPUT_TOKENS) {
            throw new IllegalStateException("The agent prompt leaves insufficient context for a model response.");
        }
        int outputTokens = (int) Math.min(configuredLimit.tokens(), available);
        OpenAiChatOptions.Builder builder = configuredOptionsBuilder().toolCallbacks(toolCallbacks).maxTokens(null).maxCompletionTokens(null);
        if (configuredLimit.legacy()) {
            builder.maxTokens(outputTokens);
        }
        else {
            builder.maxCompletionTokens(outputTokens);
        }
        String configuredModel = configuredModel();
        if (configuredModel != null) {
            builder.model(configuredModel);
        }
        return builder.build();
    }

    /**
     * Drives the agent loop for one Hyperion sandbox as a single fresh conversation.
     * <p>
     * The loop's only intrinsic bound is {@code maxTurns}; it enforces no wall-clock deadline. Cancellation is turn-granular — {@code cancelled} is polled once before each turn,
     * so a
     * cancel arriving mid-turn takes effect only after the current model call and its tool executions return. The caller owns prompt abort of a long-running tool: it registers a
     * cancel hook (see {@code GenerationJobService#registerCancelHook}) that tears down the sandbox session, which makes the in-flight tool call fail fast.
     * <p>
     * Equivalent to {@code runSession(systemPrompt, null, userPrompt, ...).result()}; see {@link #runSession} to span one logical conversation across multiple calls.
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
        return runSession(systemPrompt, null, userPrompt, tools, maxTurns, cancelled, usageSink, stepListener).result();
    }

    /**
     * A {@link #runSession} outcome paired with the conversation it produced, so a caller can span one logical conversation across multiple {@code runSession} calls (each with
     * its own system prompt, user prompt, and turn budget). {@code conversation} excludes the system message — the next call supplies its own via {@code systemPrompt}.
     */
    public record AgentLoopSession(AgentLoopResult result, List<Message> conversation) {
    }

    /**
     * Like {@link #run}, but accepts the conversation returned by a prior {@code runSession} call so several bounded loop invocations can share one logical conversation (the
     * model keeps everything it learned in earlier calls), and returns the resulting conversation so the caller can continue it again.
     * <p>
     * When {@code priorConversation} is {@code null} this behaves exactly like {@link #run}: a fresh two-message conversation (system, user). Otherwise the given
     * {@code systemPrompt} replaces the system message, {@code priorConversation} is spliced in after it unchanged, and {@code userPrompt} is appended as the next turn before
     * the loop continues — so in-loop compaction (which always protects the first two messages) keeps operating over the whole carried history exactly as it does within a
     * single {@link #run} call.
     *
     * @param systemPrompt      the system prompt for this call (replaces whatever system message headed the prior conversation, if any)
     * @param priorConversation the conversation returned by an earlier {@code runSession} call (system message excluded), or {@code null} to start fresh
     * @param userPrompt        this call's instruction, appended after the prior conversation (or the sole initial instruction when starting fresh)
     * @param tools             the tools object whose {@code @Tool} methods are exposed to the model (typically {@link SandboxAgentTools})
     * @param maxTurns          the hard cap on model turns for this call (safety budget)
     * @param cancelled         a supplier polled before each turn; if it returns {@code true} the loop stops cooperatively
     * @param usageSink         invoked after every successful model call (the main loop call and the summarization call) with its {@link ChatResponse}, so the caller can record
     *                              token usage; may be {@code null}
     * @param stepListener      invoked after every step with a short human-readable progress line (tool calls, completion); may be {@code null}
     * @return the loop outcome together with the resulting conversation (system message excluded), ready to be passed as {@code priorConversation} to a subsequent call
     */
    public AgentLoopSession runSession(String systemPrompt, @Nullable List<Message> priorConversation, String userPrompt, Object tools, int maxTurns, BooleanSupplier cancelled,
            @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> stepListener) {
        if (chatModel == null) {
            throw new IllegalStateException("No ChatModel is configured. Agentic generation is unavailable.");
        }
        requireTextSafe("provider/system-prompt", systemPrompt);
        requireTextSafe("provider/user-prompt", userPrompt);
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(tools).build();
        ToolCallback[] toolCallbacks = provider.getToolCallbacks();

        // The ChatModel does not auto-execute tools on call(), so the response carries raw tool calls this loop executes explicitly via toolCallingManager.executeToolCalls(...).
        // Build OpenAiChatOptions (not a generic ToolCallingChatOptions): OpenAiChatModel#buildRequestPrompt casts the runtime options to OpenAiChatOptions, so a
        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(systemPrompt));
        if (priorConversation != null) {
            conversation.addAll(priorConversation);
        }
        conversation.add(new UserMessage(userPrompt));

        Prompt prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
        String lastAssistantText = "";
        int consecutiveToolFailures = 0;
        // Context-window accounting: the previous call's real prompt-token count anchors the estimate; only messages appended since are estimated — see estimateContextTokens().
        long lastPromptTokens = 0;
        int messagesAtLastCall = 0;

        for (int turn = 1; turn <= maxTurns; turn++) {
            if (cancelled.getAsBoolean()) {
                emit(stepListener, "Cancelling generation…");
                return session(AgentLoopResult.Status.CANCELLED, turn - 1, lastAssistantText, conversation);
            }
            // Tag any out-of-band events the tools emit this turn (e.g. streamed file changes) with the current turn number.
            if (tools instanceof TurnAware turnAware) {
                turnAware.onTurn(turn);
            }

            messagesAtLastCall = conversation.size();
            ChatResponse response = callModel(prompt, turn, cancelled, usageSink, stepListener);
            if (response == null) {
                if (cancelled.getAsBoolean()) {
                    return session(AgentLoopResult.Status.CANCELLED, turn, lastAssistantText, conversation);
                }
                return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
            }
            // Strip leaked harmony control tokens from tool names before dispatch (see normalizeToolNames).
            response = normalizeToolNames(response);
            lastPromptTokens = promptTokensOf(response);
            if (cancelled.getAsBoolean()) {
                emit(stepListener, "Cancelling generation…");
                return session(AgentLoopResult.Status.CANCELLED, turn, lastAssistantText, conversation);
            }

            String assistantText = extractText(response);
            if (assistantText != null && !assistantText.isBlank()) {
                lastAssistantText = assistantText;
            }

            if (!response.hasToolCalls()) {
                // No more tool calls: the model considers the task complete; the verifier decides whether it actually is. Append this final turn before returning (unlike the
                // mid-loop reassignment below, nothing else in this call needs `conversation` updated afterwards) so a caller carrying the conversation forward via
                // runSession(...) does not lose the model's closing message.
                emit(stepListener, "Preparing the exercise for verification.");
                List<Message> completedConversation = new ArrayList<>(conversation);
                completedConversation.add(response.getResult().getOutput());
                return session(AgentLoopResult.Status.COMPLETED, turn, lastAssistantText, completedConversation);
            }

            List<AssistantMessage.ToolCall> toolCalls = response.getResult() != null && response.getResult().getOutput() != null ? response.getResult().getOutput().getToolCalls()
                    : List.of();
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                if (!SUBMIT_TOOL_NAME.equals(toolCall.name())) {
                    emit(stepListener, describeToolProgress(toolCall));
                }
            }
            boolean submitRequested = toolCalls.stream().anyMatch(toolCall -> SUBMIT_TOOL_NAME.equals(toolCall.name()));

            ToolExecutionResult toolExecutionResult;
            try {
                toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
                if (isSandboxSessionTerminated(tools)) {
                    emit(stepListener, "The build environment stopped responding.");
                    return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
                }
                consecutiveToolFailures = 0;
            }
            catch (RuntimeException e) {
                if (hasCause(e, LocalCIException.class)) {
                    log.warn("Agent loop lost its sandbox on turn {} ({})", turn, e.getClass().getSimpleName());
                    emit(stepListener, "The build environment stopped responding.");
                    return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
                }
                // Unknown tool / malformed arguments surface here: feed the error back so the model can self-correct, only giving up after MAX_CONSECUTIVE_TOOL_FAILURES.
                consecutiveToolFailures++;
                log.warn("Agent loop tool execution failed on turn {} (consecutive failures: {}, type: {})", turn, consecutiveToolFailures, e.getClass().getSimpleName());
                emit(stepListener, "The agent tried an unavailable action and is correcting it.");
                if (consecutiveToolFailures >= MAX_CONSECUTIVE_TOOL_FAILURES) {
                    return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
                }
                AssistantMessage failedTurn = response.getResult().getOutput();
                conversation.add(failedTurn);
                // Must answer every requested call id, or the chat-completions tool-pairing contract is violated; per-call errors also tell the model which call failed.
                List<ToolResponseMessage.ToolResponse> errorResponses = failedTurn.getToolCalls().stream()
                        .map(toolCall -> new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), "ERROR: this tool call could not be executed: " + e.getMessage()
                                + ". Only use the available tools (read_file, write_file, edit_file, delete_file, bash, verify, submit) with valid JSON arguments, then continue."))
                        .toList();
                // The catch is only reachable when this turn had tool calls (the no-tool-calls path returns above), so errorResponses always covers at least one call id.
                conversation.add(ToolResponseMessage.builder().responses(errorResponses).build());
                conversation = compactIfNeeded(conversation, lastPromptTokens, messagesAtLastCall, usageSink, cancelled, stepListener);
                prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
                continue;
            }

            // Rebuild from the executed tool-call history (covers both the submit and the continuing paths), so a carried conversation reflects the submit turn too.
            conversation = new ArrayList<>(toolExecutionResult.conversationHistory());
            // Bound each result as it enters the context, so one oversized build log cannot blow the window before compaction runs.
            capToolResponses(conversation);

            if (submitRequested) {
                // End the loop so the authoritative post-loop verifier can determine save eligibility before the quality review optionally requests repairs.
                emit(stepListener, "Submitting the exercise for verification.");
                return session(AgentLoopResult.Status.COMPLETED, turn, lastAssistantText, conversation);
            }

            // Budget-pressure nudge, appended after the conversation is rebuilt from the tool-execution history (otherwise it would be discarded with that rebuild).
            if (turn == maxTurns - 1) {
                conversation.add(new UserMessage("You are close to the step limit. Finish the current change, make sure the build and tests reflect the intended outcome, "
                        + "and then stop calling tools."));
            }
            conversation = compactIfNeeded(conversation, lastPromptTokens, messagesAtLastCall, usageSink, cancelled, stepListener);
            prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
        }

        emit(stepListener, "The generation step limit was reached.");
        return session(AgentLoopResult.Status.BUDGET_EXHAUSTED, maxTurns, lastAssistantText, conversation);
    }

    /** Builds a session result, stripping the leading system message so the returned conversation is ready to pass as {@code priorConversation} to the next call. */
    private static AgentLoopSession session(AgentLoopResult.Status status, int turns, String finalMessage, List<Message> conversation) {
        List<Message> withoutSystemMessage = conversation.isEmpty() ? List.of() : new ArrayList<>(conversation.subList(1, conversation.size()));
        return new AgentLoopSession(new AgentLoopResult(status, turns, finalMessage), withoutSystemMessage);
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSandboxSessionTerminated(Object tools) {
        return switch (tools) {
            case SandboxAgentTools sandboxTools -> sandboxTools.isSandboxSessionTerminated();
            case FileChangeEmittingAgentTools emittingTools -> emittingTools.isSandboxSessionTerminated();
            default -> false;
        };
    }

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
        return name.substring(0, name.indexOf("<|")).strip();
    }

    /** Builds instructor-facing progress without exposing raw commands or model-generated arguments. */
    private static String describeToolProgress(AssistantMessage.ToolCall toolCall) {
        String path = sanitizeProgressPath(extractJsonStringValue(toolCall.arguments() == null ? "" : toolCall.arguments(), "path"));
        return switch (toolCall.name()) {
            case "read_file" -> path == null ? "Reviewing an exercise file." : "Reviewing " + path + ".";
            case "write_file", "edit_file" -> path == null ? "Working on an exercise file." : "Working on " + path + ".";
            case "bash" -> "Running a workspace command.";
            case "verify" -> "Checking the exercise.";
            default -> "Continuing the exercise update.";
        };
    }

    @Nullable
    private static String sanitizeProgressPath(@Nullable String path) {
        if (path == null) {
            return null;
        }
        HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(path, new byte[0], HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION);
        if (!assessment.isSafe()) {
            return assessment.safePath();
        }
        String sanitized = UNSAFE_PROGRESS_CHARACTERS.matcher(path).replaceAll(" ").replaceAll("\\s+", " ").strip();
        if (sanitized.isEmpty()) {
            return null;
        }
        if (sanitized.codePointCount(0, sanitized.length()) <= MAX_PROGRESS_PATH_CHARS) {
            return sanitized;
        }
        int end = sanitized.offsetByCodePoints(0, MAX_PROGRESS_PATH_CHARS - 1);
        return sanitized.substring(0, end) + "…";
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
     * Test hook: shrink the empty-response re-sampling backoff so tests do not wait.
     *
     * @param baseMillis the backoff base
     * @param capMillis  the backoff cap
     */
    void setEmptyResponseRetryTimingForTests(long baseMillis, long capMillis) {
        this.emptyResponseRetryBaseMillis = baseMillis;
        this.emptyResponseRetryCapMillis = capMillis;
    }

    /**
     * Calls the model and re-samples only a successful response with no usable content. The OpenAI SDK already retries transport failures; retrying those again here would multiply
     * one logical turn into a request storm. Returns {@code null} when the SDK call fails or both samples are empty.
     */
    @Nullable
    private ChatResponse callModel(Prompt prompt, int turn, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> stepListener) {
        String providerFailureKey = ProviderFailureCooldown.keyForModel(configuredModel());
        for (int sample = 1; sample <= EMPTY_RESPONSE_SAMPLES; sample++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            try {
                requirePromptSafe(prompt);
                // A thrown call yields no response to meter; its spend is bounded by the retry policy and turn budget,
                // and the loop's own error handling reports the failure. Only a successful response feeds the usage sink.
                ChatResponse response = providerFailureCooldown.execute(providerFailureKey, providerHardFailureCooldown, () -> chatModel.call(prompt));
                emitUsage(usageSink, response);
                if (!isEmptyResponse(response)) {
                    return response;
                }
                // No tool calls and no text: a genuinely-flaky empty sample. Re-sample rather than treat it as a silent completion.
                log.warn("Agent loop model call returned an empty response on turn {} (sample {}/{})", turn, sample, EMPTY_RESPONSE_SAMPLES);
                if (sample < EMPTY_RESPONSE_SAMPLES) {
                    emit(stepListener, "Model returned an empty response; retrying.");
                    if (!backOffBeforeEmptyResponseRetry(sample, turn, cancelled)) {
                        return null;
                    }
                }
            }
            catch (RuntimeException e) {
                log.error("Agent loop model call failed on turn {} after the provider retry policy was exhausted ({})", turn, e.getClass().getSimpleName());
                emit(stepListener, "The AI service could not complete the request.");
                return null;
            }
        }
        log.warn("Agent loop model call returned an empty response on turn {} after {} samples", turn, EMPTY_RESPONSE_SAMPLES);
        emit(stepListener, "The AI service returned no usable response.");
        return null;
    }

    /**
     * Sleeps with jitter before re-sampling an empty response.
     *
     * @return {@code true} to continue, {@code false} if cancellation or interruption was observed
     */
    private boolean backOffBeforeEmptyResponseRetry(int sample, int turn, BooleanSupplier cancelled) {
        long backoff = Math.min(emptyResponseRetryCapMillis, emptyResponseRetryBaseMillis * (1L << (sample - 1)));
        if (backoff <= 0) {
            return !cancelled.getAsBoolean();
        }
        backoff += ThreadLocalRandom.current().nextLong(emptyResponseRetryBaseMillis + 1);
        try {
            Thread.sleep(backoff);
            return !cancelled.getAsBoolean();
        }
        catch (InterruptedException ie) {
            // Honour the interrupt instead of swallowing it.
            Thread.currentThread().interrupt();
            log.warn("Interrupted while backing off before re-sampling an empty model response on turn {}", turn);
            return false;
        }
    }

    /** A response carrying neither a tool call nor any assistant text — no usable content, so re-sampling can help. */
    private static boolean isEmptyResponse(@Nullable ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return true;
        }
        AssistantMessage output = response.getResult().getOutput();
        boolean hasToolCalls = output.getToolCalls() != null && !output.getToolCalls().isEmpty();
        String text = output.getText();
        return !hasToolCalls && (text == null || text.isBlank());
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
            BooleanSupplier cancelled, @Nullable Consumer<String> stepListener) {
        long contextTokens = estimateContextTokens(conversation, lastPromptTokens, messagesAtLastCall);
        if (contextTokens <= (long) contextWindowTokens - RESERVE_TOKENS) {
            return conversation;
        }
        if (cancelled.getAsBoolean()) {
            return conversation;
        }
        emit(stepListener, "Preparing the next generation step.");
        return compact(conversation, usageSink, cancelled);
    }

    /**
     * Estimates the prompt's token count: anchors on the provider's real {@code promptTokens} from the previous call (which also captures out-of-band tool-schema tokens) and adds
     * a jtokkit estimate of only the messages appended since. Before the first call the whole conversation is estimated.
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
                tokens += TOOLCALL_OVERHEAD_TOKENS + estimateTextTokens(response.responseData());
            }
            return tokens;
        }
        if (message instanceof AssistantMessage assistant) {
            tokens += estimateTextTokens(assistant.getText());
            for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                // Tokenize name and arguments separately, then add the structural framing overhead the text estimator does not account for.
                tokens += TOOLCALL_OVERHEAD_TOKENS + estimateTextTokens(toolCall.name()) + estimateTextTokens(toolCall.arguments());
            }
            return tokens;
        }
        return tokens + estimateTextTokens(message.getText());
    }

    /** Exact token count of a text span via jtokkit (0 for null/empty); callers add the structural per-message/per-tool-call overheads the estimator does not see. */
    private static long estimateTextTokens(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return TEXT_TOKEN_ESTIMATOR.estimate(text);
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
        return compact(conversation, usageSink, () -> false);
    }

    List<Message> compact(List<Message> conversation, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
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
        if (cancelled.getAsBoolean()) {
            return conversation;
        }
        String summaryBody;
        try {
            summaryBody = summarize(toSummarize, usageSink, cancelled);
        }
        catch (CancellationException ignored) {
            return conversation;
        }
        catch (RuntimeException e) {
            log.warn("Compaction summarization failed ({}); dropping {} older message(s) behind a marker instead.", e.getClass().getSimpleName(), toSummarize.size());
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
    private String summarize(List<Message> messages, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        StringBuilder transcript = new StringBuilder();
        for (Message message : messages) {
            transcript.append(renderForSummary(message)).append('\n');
        }
        List<Message> summaryPrompt = List.of(new SystemMessage(SUMMARIZATION_SYSTEM_PROMPT),
                new UserMessage("Summarize the following earlier session messages into the structured summary described above:\n\n" + transcript));
        // OpenAiChatOptions with no tool callbacks so the summarizer cannot call tools; the output cap keeps the summary small. Must be OpenAiChatOptions (not a generic
        // ChatOptions): OpenAiChatModel#buildRequestPrompt casts the runtime options to OpenAiChatOptions, so a DefaultChatOptions would throw ClassCastException. Model pinned
        // as in the agent loop (see configuredModel()).
        // Compaction is a bounded factual summary, not an authoring decision. Use explicit low reasoning when the provider enables reasoning so its server default cannot consume
        // the
        // small summary allowance; omit the field for providers that do not support it.
        OpenAiChatOptions.Builder summaryOptions = configuredOptionsBuilder().toolCallbacks(List.of()).reasoningEffort(hasConfiguredReasoningEffort() ? "low" : null)
                .maxTokens(null).maxCompletionTokens(null);
        int summaryOutputTokens = Math.min(SUMMARY_MAX_OUTPUT_TOKENS, configuredTurnTokenLimit().tokens());
        if (usesLegacyMaxTokens()) {
            summaryOptions.maxTokens(summaryOutputTokens);
        }
        else {
            summaryOptions.maxCompletionTokens(summaryOutputTokens);
        }
        String configuredModel = configuredModel();
        if (configuredModel != null) {
            summaryOptions.model(configuredModel);
        }
        Prompt prompt = new Prompt(summaryPrompt, summaryOptions.build());
        requirePromptSafe(prompt);
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Generation was cancelled before conversation compaction");
        }
        ChatResponse response = providerFailureCooldown.execute(ProviderFailureCooldown.keyForModel(configuredModel), providerHardFailureCooldown, () -> chatModel.call(prompt));
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

    private static void requirePromptSafe(Prompt prompt) {
        int messageIndex = 0;
        for (Message message : prompt.getInstructions()) {
            if (message instanceof ToolResponseMessage toolResponse) {
                int responseIndex = 0;
                for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                    requireTextSafe("provider/tool-observation-" + messageIndex + "-" + responseIndex, response.responseData());
                    responseIndex++;
                }
            }
            else {
                requireTextSafe("provider/message-" + messageIndex, message.getText());
            }
            messageIndex++;
        }
    }

    private static void requireTextSafe(String logicalPath, @Nullable String text) {
        SECRET_MATERIAL_POLICY.requireSafe(logicalPath, text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                HyperionSecretMaterialPolicy.Origin.PROVIDER_PROMPT);
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
