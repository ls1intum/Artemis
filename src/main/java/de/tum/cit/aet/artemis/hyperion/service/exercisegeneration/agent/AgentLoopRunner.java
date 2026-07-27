package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.nio.charset.StandardCharsets;
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
import java.util.stream.Collectors;

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
 * stops, the turn budget is reached, cancellation is requested, or an error occurs. The loop is manual because Spring AI's automatic tool execution has no iteration cap and no
 * per-step hook, so it can enforce neither the safety budget nor the transcript. Artifact correctness is decided separately by the authoritative verifier.
 * <p>
 * This rests on the {@link ChatModel} contract: a model call returns the requested tool calls UNEXECUTED and never runs them itself, so
 * executing them through {@code toolCallingManager.executeToolCalls} and feeding the results back is this loop's obligation, not an optimization. A model implementation that
 * executed tools internally would silently bypass every budget, veto, and transcript rule below.
 * <p>
 * The loop's only intrinsic bound is {@code maxTurns}; it enforces no wall-clock deadline. Cancellation is turn-granular — {@code cancelled} is polled once before each turn, so a
 * cancel arriving mid-turn takes effect only after the current model call and its tool executions return. Prompt abort of a long-running tool is the caller's: it registers a
 * cancel hook that tears down the sandbox session, which makes the in-flight tool call fail fast.
 */
public class AgentLoopRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopRunner.class);

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private static final int MAX_CONSECUTIVE_TOOL_FAILURES = 5;

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
     * Pinned to {@code o200k_base}, the tokenizer of the GPT-4o/GPT-5-class models Hyperion is configured against ({@code gpt-5-mini} by default), rather than the library
     * default {@code cl100k_base}, which belongs to the older GPT-4/3.5 families and would mis-size the context of every model actually used here — under-counting risks a
     * provider-side context overflow, over-counting compacts a conversation that still fits. It counts message text only; the per-message structural overheads
     * below (envelope, tool-call framing) are added on top because the estimator cannot see them.
     */
    private static final JTokkitTokenCountEstimator TEXT_TOKEN_ESTIMATOR = new JTokkitTokenCountEstimator(EncodingType.O200K_BASE);

    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    private static final int TOOLCALL_OVERHEAD_TOKENS = 8;

    /**
     * Hard cap on a single tool result kept in the live context; head and tail (where the signal lives) are kept and the middle is elided. Package-private because every tool
     * that caps its own inline output must stay below it (see {@code SandboxAgentTools}), or that tool's "output was truncated" marker describes an elision this loop then
     * silently redoes.
     */
    static final int MAX_TOOL_RESPONSE_CHARS = 12_000;

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

    public AgentLoopResult run(String systemPrompt, String userPrompt, Object tools, int maxTurns, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink,
            @Nullable Consumer<String> stepListener) {
        return runSession(systemPrompt, null, userPrompt, tools, maxTurns, cancelled, usageSink, stepListener).result();
    }

    /** {@code conversation} excludes the system message, because the next {@code runSession} call supplies its own via {@code systemPrompt}. */
    public record AgentLoopSession(AgentLoopResult result, List<Message> conversation) {
    }

    /**
     * Drives one bounded agent loop, optionally continuing a conversation an earlier call produced so several loop invocations share one logical conversation. {@link #run} is the
     * single-shot form.
     * <p>
     * With no prior conversation this starts a fresh two-message conversation (system, user). Otherwise {@code systemPrompt} replaces the system message, the prior conversation
     * is spliced in after it unchanged, and {@code userPrompt} is appended as the next turn — so in-loop compaction, which always protects the first two messages, operates over
     * the whole carried history rather than only this call's part of it.
     *
     * @param systemPrompt      the system prompt for this call, replacing whatever system message headed the prior conversation
     * @param priorConversation the conversation an earlier call returned (system message excluded), or {@code null} to start fresh
     * @param userPrompt        this call's instruction, appended after the prior conversation
     * @param tools             the tools object whose {@code @Tool} methods are exposed to the model (typically {@link SandboxAgentTools})
     * @param maxTurns          the hard cap on model turns for this call
     * @param cancelled         polled before each turn; {@code true} stops the loop cooperatively
     * @param usageSink         invoked with the {@link ChatResponse} of every successful model call, including the summarization call, so the caller can record token usage
     * @param stepListener      invoked after every step with a short human-readable progress line
     * @return the loop outcome and the resulting conversation, ready to be passed as {@code priorConversation} to a subsequent call
     */
    public AgentLoopSession runSession(String systemPrompt, @Nullable List<Message> priorConversation, String userPrompt, Object tools, int maxTurns, BooleanSupplier cancelled,
            @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> stepListener) {
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(tools).build();
        return runSessionWithCallbacks(systemPrompt, priorConversation, userPrompt, tools, provider.getToolCallbacks(), maxTurns, cancelled, usageSink, stepListener);
    }

    public AgentLoopSession runTextSession(String systemPrompt, @Nullable List<Message> priorConversation, String userPrompt, int maxTurns, BooleanSupplier cancelled,
            @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> stepListener) {
        return runSessionWithCallbacks(systemPrompt, priorConversation, userPrompt, null, new ToolCallback[0], maxTurns, cancelled, usageSink, stepListener);
    }

    private AgentLoopSession runSessionWithCallbacks(String systemPrompt, @Nullable List<Message> priorConversation, String userPrompt, @Nullable Object tools,
            ToolCallback[] toolCallbacks, int maxTurns, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> stepListener) {
        if (chatModel == null) {
            throw new IllegalStateException("No ChatModel is configured. Agentic generation is unavailable.");
        }
        requireTextSafe("provider/system-prompt", systemPrompt);
        requireTextSafe("provider/user-prompt", userPrompt);

        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(systemPrompt));
        if (priorConversation != null) {
            conversation.addAll(priorConversation);
        }
        conversation.add(new UserMessage(userPrompt));

        Prompt prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
        String lastAssistantText = "";
        int consecutiveToolFailures = 0;
        long lastPromptTokens = 0;
        int messagesAtLastCall = 0;

        for (int turn = 1; turn <= maxTurns; turn++) {
            if (cancelled.getAsBoolean()) {
                emit(stepListener, "Cancelling generation…");
                return session(AgentLoopResult.Status.CANCELLED, turn - 1, lastAssistantText, conversation);
            }
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
                // The model considers the task complete; the verifier decides whether it actually is. Append this closing turn before returning so a caller carrying the
                // conversation forward does not lose it.
                emit(stepListener, "Preparing the exercise for verification.");
                List<Message> completedConversation = new ArrayList<>(conversation);
                completedConversation.add(response.getResult().getOutput());
                return session(AgentLoopResult.Status.COMPLETED, turn, lastAssistantText, completedConversation);
            }

            List<AssistantMessage.ToolCall> toolCalls = response.getResult() != null && response.getResult().getOutput() != null ? response.getResult().getOutput().getToolCalls()
                    : List.of();

            // A completion cut off by the token limit may carry silently truncated tool arguments (half a file in write_file's content). Executing them would corrupt the
            // workspace while the model believes the calls landed, so every call id is answered with a re-issue instruction instead.
            if (isTruncatedByTokenLimit(response) && !toolCalls.isEmpty()) {
                emit(stepListener, "The response was cut off at the length limit; asking the agent to re-issue its last actions.");
                conversation.add(response.getResult().getOutput());
                List<ToolResponseMessage.ToolResponse> truncatedResponses = toolCalls.stream()
                        .map(toolCall -> new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(),
                                "ERROR: this tool call was not executed - the response hit the output token limit, so its arguments may be truncated. "
                                        + "Re-issue the call with complete arguments, splitting large content across smaller calls if needed."))
                        .toList();
                conversation.add(ToolResponseMessage.builder().responses(truncatedResponses).build());
                conversation = compactIfNeeded(conversation, lastPromptTokens, messagesAtLastCall, usageSink, cancelled, stepListener);
                prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
                continue;
            }

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
                // Unknown tool or malformed arguments surface here: feed the error back so the model can self-correct rather than failing the run on one bad call.
                consecutiveToolFailures++;
                log.warn("Agent loop tool execution failed on turn {} (consecutive failures: {}, type: {})", turn, consecutiveToolFailures, e.getClass().getSimpleName());
                // Tool names are model-chosen identifiers, not user content, so naming them here is safe; arguments and paths are not (see sanitizeProgressPath).
                emit(stepListener, "The agent tried an unavailable action (" + attemptedToolNames(response) + ") and is correcting it.");
                if (consecutiveToolFailures >= MAX_CONSECUTIVE_TOOL_FAILURES) {
                    return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
                }
                AssistantMessage failedTurn = response.getResult().getOutput();
                conversation.add(failedTurn);
                // Every requested call id must be answered, or the chat-completions tool-pairing contract is violated on the next request.
                List<ToolResponseMessage.ToolResponse> errorResponses = failedTurn.getToolCalls().stream()
                        .map(toolCall -> new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), "ERROR: this tool call could not be executed: " + e.getMessage()
                                + ". Only use the available tools (read_file, write_file, edit_file, delete_file, bash, verify, submit) with valid JSON arguments, then continue."))
                        .toList();
                conversation.add(ToolResponseMessage.builder().responses(errorResponses).build());
                conversation = compactIfNeeded(conversation, lastPromptTokens, messagesAtLastCall, usageSink, cancelled, stepListener);
                prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
                continue;
            }

            // Rebuild from the executed tool-call history so a carried conversation reflects the submit turn too.
            conversation = new ArrayList<>(toolExecutionResult.conversationHistory());
            // Bound each result as it enters the context, so one oversized build log cannot blow the window before compaction runs.
            capToolResponses(conversation);

            if (submitRequested) {
                if (isSubmitVetoed(tools)) {
                    // The rejection message is already the tool result in `conversation`, so falling through to the ordinary next-turn handling lets the model fix and resubmit.
                    emit(stepListener, "Submit was rejected by the stage check; continuing to address the reported issues.");
                }
                else {
                    emit(stepListener, "Submitting the exercise for verification.");
                    return session(AgentLoopResult.Status.COMPLETED, turn, lastAssistantText, conversation);
                }
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

    /** Whether the provider reports this completion was cut off by the output token limit (OpenAI-style finish reason "length"). */
    private static boolean isTruncatedByTokenLimit(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getMetadata() == null) {
            return false;
        }
        return "length".equalsIgnoreCase(response.getResult().getMetadata().getFinishReason());
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

    private static boolean isSubmitVetoed(Object tools) {
        return tools instanceof SubmitVetoAware vetoAware && vetoAware.consumeSubmitVeto();
    }

    /**
     * Removes leaked harmony control tokens from tool-call names, so a name like {@code bash<|channel|>commentary} dispatches as {@code bash} instead of matching no registered
     * tool. Rebuilds the response only when a name actually changes, which is the rare case.
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
            case "delete_file" -> path == null ? "Removing an exercise file." : "Removing " + path + ".";
            case "submit" -> "Submitting the current work for checking.";
            default -> "Continuing the exercise update.";
        };
    }

    @Nullable
    private static String attemptedToolNames(ChatResponse response) {
        try {
            String names = response.getResult().getOutput().getToolCalls().stream().map(AssistantMessage.ToolCall::name)
                    .map(name -> UNSAFE_PROGRESS_CHARACTERS.matcher(name == null ? "" : name).replaceAll("")).filter(name -> !name.isBlank()).distinct()
                    .collect(Collectors.joining(", "));
            return names.isBlank() ? "unknown" : names.length() > 80 ? names.substring(0, 80) : names;
        }
        catch (RuntimeException e) {
            return "unknown";
        }
    }

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

    @Nullable
    private static String extractJsonStringValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");
    }

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
                // Only a successful response feeds the usage sink: a thrown call yields nothing to meter, and its spend is already bounded by the retry policy and turn budget.
                ChatResponse response = providerFailureCooldown.execute(providerFailureKey, providerHardFailureCooldown, () -> chatModel.call(prompt));
                emitUsage(usageSink, response);
                if (!isEmptyResponse(response)) {
                    return response;
                }
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
                tokens += TOOLCALL_OVERHEAD_TOKENS + estimateTextTokens(toolCall.name()) + estimateTextTokens(toolCall.arguments());
            }
            return tokens;
        }
        return tokens + estimateTextTokens(message.getText());
    }

    private static long estimateTextTokens(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return TEXT_TOKEN_ESTIMATOR.estimate(text);
    }

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
        // head + marker + tail must stay within MAX_TOOL_RESPONSE_CHARS.
        int head = MAX_TOOL_RESPONSE_CHARS / 4;
        int elidedEstimate = data.length() - MAX_TOOL_RESPONSE_CHARS;
        String marker = "\n[… " + elidedEstimate
                + " characters elided to fit the context window. Re-fetch just the part you need: read_file with offset/limit, or grep via bash. …]\n";
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
        long budget = (long) contextWindowTokens - RESERVE_TOKENS;
        long fixed = estimateTokens(conversation, 0, protectedPrefix) + SUMMARY_MAX_OUTPUT_TOKENS + MESSAGE_OVERHEAD_TOKENS;
        while (cut < n && fixed + estimateTokens(conversation, cut, n) > budget) {
            cut = snapToTurnStart(conversation, cut + 1);
        }
        if (cut == n) {
            // Even the minimal tail does not fit, so the conversation becomes summary-only. The per-result caps make this unreachable in practice; log it if it ever happens.
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
        // No tool callbacks, so the summarizer cannot call tools. The options must be OpenAiChatOptions rather than a generic ChatOptions: OpenAiChatModel#buildRequestPrompt
        // casts the runtime options, so a DefaultChatOptions throws ClassCastException. Compaction is a bounded factual summary, not an authoring decision, so reasoning effort
        // is pinned low where the provider supports it — a server-side reasoning default would otherwise consume the whole summary allowance.
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
        SECRET_MATERIAL_POLICY.requireSafe(logicalPath, text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8), HyperionSecretMaterialPolicy.Origin.PROVIDER_PROMPT);
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
