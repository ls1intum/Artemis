package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Drives the Spring AI tool-calling loop for agentic exercise generation: repeatedly calls the model, executes the requested tools, and feeds the results back until the model
 * stops, the turn budget is reached, cancellation is requested, or an error occurs. The loop is manual because Spring AI's automatic tool execution has no iteration cap and no
 * per-step hook, so it can enforce neither the safety budget nor the transcript. Artifact correctness is decided separately by the authoritative verifier.
 * <p>
 * This rests on the {@link ChatModel} contract that a model call returns the requested tool calls UNEXECUTED: a model implementation that executed them internally would
 * silently bypass every budget, veto, and transcript rule below.
 * <p>
 * The loop's only intrinsic bound is {@code maxTurns}; it enforces no wall-clock deadline. Cancellation is turn-granular — {@code cancelled} is polled once before each turn, so a
 * cancel arriving mid-turn takes effect only after the current model call and its tool executions return. Prompt abort of a long-running tool is the caller's: it registers a
 * cancel hook that tears down the sandbox session, which makes the in-flight tool call fail fast.
 */
public class AgentLoopRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopRunner.class);

    private static final int MAX_CONSECUTIVE_TOOL_FAILURES = 5;

    private static final String SUBMIT_TOOL_NAME = "submit";

    /** Emitted immediately before every provider call; see {@link #emitWaitingOnModel}. */
    static final String WAITING_ON_MODEL_MESSAGE = "Thinking about the next step.";

    /** Headroom reserved below the context window for the default response allowance plus estimation slack. */
    private static final int RESERVE_TOKENS = 20_480;

    private static final int CONTEXT_ESTIMATION_SAFETY_TOKENS = 4_096;

    private static final int MIN_TURN_OUTPUT_TOKENS = 1_024;

    /** Target size of the verbatim recent tail kept across a compaction (everything older is summarized). */
    private static final int KEEP_RECENT_TOKENS = 20_000;

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

    private final AgentCheckpointManager checkpointManager;

    private final String checkpointProviderContract;

    @Nullable
    private final ChatModel chatModel;

    /** The unwrapped provider bean, kept only so the checkpoint provider contract fingerprints the configured implementation rather than this loop's scrubbing decorator. */
    @Nullable
    private final ChatModel contractModel;

    /**
     * The options every request for this runner starts from: the effort profile's prebuilt provider options when the run selected one, and otherwise the configured
     * {@link ChatModel}'s own options. Resolved once so the model id, the reasoning effort, the output-token limit, and the checkpoint provider contract cannot read from
     * different sources.
     */
    @Nullable
    private final ChatOptions effectiveOptions;

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
        this(chatModels, contextWindowTokens, providerHardFailureCooldown, providerFailureCooldown, new AgentCheckpointManager(new ObjectMapper(), "", "", 0, false, ""));
    }

    public AgentLoopRunner(Collection<ChatModel> chatModels, int contextWindowTokens, Duration providerHardFailureCooldown, ProviderFailureCooldown providerFailureCooldown,
            AgentCheckpointManager checkpointManager) {
        ChatModel configuredChatModel = chatModels.isEmpty() ? null : chatModels.iterator().next();
        this.chatModel = configuredChatModel == null ? null : new HarmonyScrubbingChatModel(configuredChatModel);
        this.contractModel = configuredChatModel;
        this.effectiveOptions = configuredChatModel == null ? null : configuredChatModel.getOptions();
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.contextWindowTokens = contextWindowTokens;
        this.providerHardFailureCooldown = providerHardFailureCooldown;
        this.providerFailureCooldown = providerFailureCooldown;
        this.checkpointManager = checkpointManager;
        this.checkpointProviderContract = checkpointManager.providerContract(configuredChatModel, contextWindowTokens, this.effectiveOptions, "");
    }

    /** Derivation constructor for {@link #forSettings}; shares the wrapped model, tool manager, cooldown state, and checkpoint manager of the deployment-wide runner. */
    private AgentLoopRunner(AgentLoopRunner deploymentRunner, HyperionGenerationSettings settings) {
        this.chatModel = deploymentRunner.chatModel;
        this.contractModel = deploymentRunner.contractModel;
        this.effectiveOptions = settings.chatOptions() != null ? settings.chatOptions() : deploymentRunner.effectiveOptions;
        this.toolCallingManager = deploymentRunner.toolCallingManager;
        this.contextWindowTokens = settings.contextWindowTokens();
        this.providerHardFailureCooldown = deploymentRunner.providerHardFailureCooldown;
        this.providerFailureCooldown = deploymentRunner.providerFailureCooldown;
        this.checkpointManager = deploymentRunner.checkpointManager;
        this.emptyResponseRetryBaseMillis = deploymentRunner.emptyResponseRetryBaseMillis;
        this.emptyResponseRetryCapMillis = deploymentRunner.emptyResponseRetryCapMillis;
        this.checkpointProviderContract = checkpointManager.providerContract(this.contractModel, contextWindowTokens, this.effectiveOptions, settings.name(),
                settings.maxTokensPerJob(), settings.maxJobDuration());
    }

    /**
     * A runner configured for one run's effort profile. The profile's prebuilt options replace the model bean's defaults for every call this runner makes — the agent turn, the
     * compaction summary, and the empty-response re-sample alike.
     *
     * @param settings the resolved settings of the run
     * @return this runner when the settings are the deployment default, or a runner pinned to the profile otherwise
     */
    public AgentLoopRunner forSettings(@Nullable HyperionGenerationSettings settings) {
        return settings == null ? this : new AgentLoopRunner(this, settings);
    }

    /**
     * The model id this runner's effective options were set up with. It is pinned on every request because Spring AI uses prompt options in place of model defaults when they are
     * present.
     */
    @Nullable
    private String configuredModel() {
        return effectiveOptions == null ? null : effectiveOptions.getModel();
    }

    private OpenAiChatOptions.Builder configuredOptionsBuilder() {
        return effectiveOptions instanceof OpenAiChatOptions openAiDefaults ? openAiDefaults.mutate() : OpenAiChatOptions.builder();
    }

    public void beginCheckpointRun(String jobId, ProgrammingExercise exercise, SandboxAgentTools tools, ApprovedSpecRegistry approvedSpecs) {
        checkpointManager.beginRun(jobId, exercise, tools, approvedSpecs);
    }

    public void endCheckpointRun() {
        checkpointManager.endRun();
    }

    private boolean hasConfiguredReasoningEffort() {
        return effectiveOptions instanceof OpenAiChatOptions openAiDefaults && openAiDefaults.getReasoningEffort() != null;
    }

    private record TurnTokenLimit(boolean legacy, int tokens) {
    }

    private TurnTokenLimit configuredTurnTokenLimit() {
        if (effectiveOptions instanceof OpenAiChatOptions openAiDefaults && openAiDefaults.getMaxCompletionTokens() != null) {
            return new TurnTokenLimit(false, openAiDefaults.getMaxCompletionTokens());
        }
        if (effectiveOptions != null && effectiveOptions.getMaxTokens() != null) {
            return new TurnTokenLimit(true, effectiveOptions.getMaxTokens());
        }
        return new TurnTokenLimit(false, TURN_MAX_OUTPUT_TOKENS);
    }

    private boolean usesLegacyMaxTokens() {
        return configuredTurnTokenLimit().legacy();
    }

    private OpenAiChatOptions agentOptions(ToolCallback[] toolCallbacks, List<Message> conversation) {
        TurnTokenLimit configuredLimit = configuredTurnTokenLimit();
        long available = (long) contextWindowTokens - AgentConversationContext.estimateTokens(conversation, 0, conversation.size()) - CONTEXT_ESTIMATION_SAFETY_TOKENS;
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
        if (chatModel == null && !checkpointManager.replaysAllAuthoringCalls()) {
            throw new IllegalStateException("No ChatModel is configured. Agentic generation is unavailable.");
        }
        AgentPromptSafety.requireTextSafe("provider/system-prompt", systemPrompt);
        AgentPromptSafety.requireTextSafe("provider/user-prompt", userPrompt);

        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(systemPrompt));
        if (priorConversation != null) {
            conversation.addAll(priorConversation);
        }
        conversation.add(new UserMessage(userPrompt));

        Prompt prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
        String lastAssistantText = "";
        int consecutiveToolFailures = 0;
        int consecutiveRejectedActions = 0;
        long lastPromptTokens = 0;
        int messagesAtLastCall = 0;
        boolean checkpointsEnabled = checkpointManager.enabled();
        String toolContract = checkpointsEnabled ? checkpointManager.toolContract(toolCallbacks) : "";
        // Resolved from the sink rather than passed in: the runner is a shared bean, so it must hold no per-run state of its own.
        GenerationActivityTracker activity = AgentActivitySink.trackerOf(stepListener);

        for (int turn = 1; turn <= maxTurns; turn++) {
            if (cancelled.getAsBoolean()) {
                emit(stepListener, "Cancelling generation…");
                return session(AgentLoopResult.Status.CANCELLED, turn - 1, lastAssistantText, conversation);
            }
            // Recorded unconditionally and before anything can fail, so a run abandoned at a gate still reports the turns it spent.
            recordTurn(usageSink);
            if (activity != null) {
                activity.turn(turn);
            }
            if (tools instanceof TurnAware turnAware) {
                turnAware.onTurn(turn);
            }

            // Check the complete carried prompt before checkpointing it. Tool observations can introduce secret material after the initial system/user checks; persisting first
            // would leak content that the provider boundary correctly rejects below.
            AgentPromptSafety.requirePromptSafe(prompt);
            AgentCheckpointManager.TurnHandle checkpoint = checkpointsEnabled ? checkpointManager.beforeTurn(turn, maxTurns, checkpointProviderContract, toolContract, conversation,
                    new AgentCheckpointManager.LoopCursor(lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall)) : null;
            if (checkpoint != null && checkpoint.replayed()) {
                AgentCheckpointManager.CheckpointState replayed = checkpoint.replayedAfter();
                conversation = new ArrayList<>(AgentCheckpointMessageCodec.decode(replayed.conversation()));
                lastAssistantText = replayed.cursor().lastAssistantText();
                consecutiveToolFailures = replayed.cursor().consecutiveToolFailures();
                lastPromptTokens = replayed.cursor().lastPromptTokens();
                messagesAtLastCall = replayed.cursor().messagesAtLastCall();
                AgentLoopResult.Status terminalStatus = checkpoint.replayedTerminalStatus();
                if (terminalStatus != null) {
                    return session(terminalStatus, turn, lastAssistantText, conversation);
                }
                prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
                continue;
            }
            if (checkpoint != null && checkpoint.prepared()) {
                AgentCheckpointManager.CheckpointState prepared = checkpoint.before();
                conversation = new ArrayList<>(AgentCheckpointMessageCodec.decode(prepared.conversation()));
                lastAssistantText = prepared.cursor().lastAssistantText();
                consecutiveToolFailures = prepared.cursor().consecutiveToolFailures();
                lastPromptTokens = prepared.cursor().lastPromptTokens();
                messagesAtLastCall = prepared.cursor().messagesAtLastCall();
                prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
                AgentPromptSafety.requirePromptSafe(prompt);
            }

            messagesAtLastCall = conversation.size();
            emitWaitingOnModel(stepListener, activity);
            ChatResponse response = callModel(prompt, turn, cancelled, usageSink, stepListener, activity);
            if (response == null) {
                if (cancelled.getAsBoolean()) {
                    finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, AgentLoopResult.Status.CANCELLED);
                    return session(AgentLoopResult.Status.CANCELLED, turn, lastAssistantText, conversation);
                }
                finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, AgentLoopResult.Status.ERROR);
                return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
            }
            response = normalizeToolNames(response);
            lastPromptTokens = promptTokensOf(response);
            List<AssistantMessage.ToolCall> toolCalls = response.getResult() != null && response.getResult().getOutput() != null ? response.getResult().getOutput().getToolCalls()
                    : List.of();
            if (response.getResult() != null && response.getResult().getOutput() != null) {
                AgentPromptSafety.requireAssistantSafe(response.getResult().getOutput(), "provider/response");
            }
            recordToolCalls(usageSink, toolCalls.size());
            if (cancelled.getAsBoolean()) {
                emit(stepListener, "Cancelling generation…");
                finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, AgentLoopResult.Status.CANCELLED);
                return session(AgentLoopResult.Status.CANCELLED, turn, lastAssistantText, conversation);
            }

            String assistantText = extractText(response);
            if (assistantText != null && !assistantText.isBlank()) {
                lastAssistantText = assistantText;
            }

            if (!response.hasToolCalls()) {
                // Append this closing turn before returning so a caller carrying the conversation forward does not lose it.
                emit(stepListener, "Preparing the exercise for verification.");
                List<Message> completedConversation = new ArrayList<>(conversation);
                completedConversation.add(response.getResult().getOutput());
                finishCheckpoint(checkpoint, completedConversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall,
                        AgentLoopResult.Status.COMPLETED);
                return session(AgentLoopResult.Status.COMPLETED, turn, lastAssistantText, completedConversation);
            }

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
                finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, null);
                continue;
            }

            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                if (activity != null) {
                    activity.recordToolCall();
                }
                if (!SUBMIT_TOOL_NAME.equals(toolCall.name())) {
                    emit(stepListener, AgentToolProgress.describe(toolCall));
                }
            }
            boolean submitRequested = toolCalls.stream().anyMatch(toolCall -> SUBMIT_TOOL_NAME.equals(toolCall.name()));
            ToolExecutionResult toolExecutionResult;
            try {
                toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
                // Rebuild immediately so even a terminal sandbox failure has a lossless checkpoint of the model call and every tool result produced before termination.
                conversation = new ArrayList<>(toolExecutionResult.conversationHistory());
                AgentConversationContext.capToolResponses(conversation);
                if (isSandboxSessionTerminated(tools)) {
                    emit(stepListener, "The build environment stopped responding.");
                    finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, AgentLoopResult.Status.ERROR);
                    return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
                }
                if (hasRejectedToolAction(conversation)) {
                    if (hasOnlyRecoverableFileToolRejections(conversation)) {
                        consecutiveRejectedActions++;
                        consecutiveToolFailures = 0;
                        log.warn("Agent loop file-tool action was rejected on turn {} (consecutive recoverable rejections: {})", turn, consecutiveRejectedActions);
                        if (consecutiveRejectedActions == 2) {
                            conversation.add(new UserMessage("Two file-tool actions were rejected. Do not repeat the same call. Re-read the target file, then use a uniquely "
                                    + "anchored edit_file call or rewrite the complete small file with write_file. Search accepts only one-line text; use read_file when locating "
                                    + "a multiline block. Continue from the unchanged workspace."));
                        }
                    }
                    else {
                        consecutiveRejectedActions = 0;
                        consecutiveToolFailures++;
                        log.warn("Agent loop tool action was rejected on turn {} (consecutive failures: {})", turn, consecutiveToolFailures);
                        if (consecutiveToolFailures >= MAX_CONSECUTIVE_TOOL_FAILURES) {
                            finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall,
                                    AgentLoopResult.Status.ERROR);
                            return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
                        }
                    }
                }
                else {
                    consecutiveToolFailures = 0;
                    consecutiveRejectedActions = 0;
                }
            }
            catch (RuntimeException e) {
                if (hasCause(e, LocalCIException.class)) {
                    log.warn("Agent loop lost its sandbox on turn {} ({})", turn, e.getClass().getSimpleName());
                    emit(stepListener, "The build environment stopped responding.");
                    finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, AgentLoopResult.Status.ERROR);
                    return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
                }
                // Unknown tool or malformed arguments surface here: feed the error back so the model can self-correct rather than failing the run on one bad call.
                consecutiveToolFailures++;
                log.warn("Agent loop tool execution failed on turn {} (consecutive failures: {}, type: {})", turn, consecutiveToolFailures, e.getClass().getSimpleName());
                // Tool names are model-chosen identifiers, not user content, so naming them here is safe; arguments and paths are not (see AgentToolProgress).
                emit(stepListener, "The agent tried an unavailable action (" + AgentToolProgress.attemptedNames(response) + ") and is correcting it.");
                AssistantMessage failedTurn = response.getResult().getOutput();
                conversation.add(failedTurn);
                // Every requested call id must be answered, or the chat-completions tool-pairing contract is violated on the next request.
                List<ToolResponseMessage.ToolResponse> errorResponses = failedTurn.getToolCalls().stream()
                        .map(toolCall -> new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), "ERROR: this tool call could not be executed: " + e.getMessage()
                                + ". Only use the available tools (read_file, write_file, edit_file, delete_file, bash, verify, submit) with valid JSON arguments, then continue."))
                        .toList();
                conversation.add(ToolResponseMessage.builder().responses(errorResponses).build());
                if (consecutiveToolFailures >= MAX_CONSECUTIVE_TOOL_FAILURES) {
                    finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, AgentLoopResult.Status.ERROR);
                    return session(AgentLoopResult.Status.ERROR, turn, lastAssistantText, conversation);
                }
                conversation = compactIfNeeded(conversation, lastPromptTokens, messagesAtLastCall, usageSink, cancelled, stepListener);
                prompt = new Prompt(conversation, agentOptions(toolCallbacks, conversation));
                finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, null);
                continue;
            }

            if (submitRequested) {
                if (isSubmitVetoed(tools)) {
                    // The rejection message is already the tool result in `conversation`, so falling through to the ordinary next-turn handling lets the model fix and resubmit.
                    emit(stepListener, "Submit was rejected by the stage check; continuing to address the reported issues.");
                }
                else {
                    emit(stepListener, "Submitting the exercise for verification.");
                    finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, AgentLoopResult.Status.COMPLETED);
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
            finishCheckpoint(checkpoint, conversation, lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall, null);
        }

        emit(stepListener, "The generation step limit was reached.");
        return session(AgentLoopResult.Status.BUDGET_EXHAUSTED, maxTurns, lastAssistantText, conversation);
    }

    private static boolean hasRejectedToolAction(List<Message> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i) instanceof ToolResponseMessage toolResponse) {
                return toolResponse.getResponses().stream().anyMatch(response -> !"verify".equals(response.name()) && isErrorResponse(response.responseData()));
            }
        }
        return false;
    }

    private static boolean hasOnlyRecoverableFileToolRejections(List<Message> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i) instanceof ToolResponseMessage toolResponse) {
                List<ToolResponseMessage.ToolResponse> rejected = toolResponse.getResponses().stream().filter(response -> isErrorResponse(response.responseData())).toList();
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

    private void finishCheckpoint(AgentCheckpointManager.TurnHandle checkpoint, List<Message> conversation, String lastAssistantText, int consecutiveToolFailures,
            long lastPromptTokens, int messagesAtLastCall, AgentLoopResult.Status terminalStatus) {
        if (checkpoint == null) {
            return;
        }
        checkpointManager.finishTurn(checkpoint, conversation,
                new AgentCheckpointManager.LoopCursor(lastAssistantText, consecutiveToolFailures, lastPromptTokens, messagesAtLastCall), terminalStatus);
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
        return tools instanceof SubmitVetoAware sandboxAware && sandboxAware.isSandboxSessionTerminated();
    }

    private static boolean isSubmitVetoed(Object tools) {
        return tools instanceof SubmitVetoAware vetoAware && vetoAware.consumeSubmitVeto();
    }

    /**
     * Removes leaked harmony control tokens from tool-call names, so a name like {@code bash<|channel|>commentary} dispatches as {@code bash} instead of matching no registered
     * tool.
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

    void setEmptyResponseRetryTimingForTests(long baseMillis, long capMillis) {
        this.emptyResponseRetryBaseMillis = baseMillis;
        this.emptyResponseRetryCapMillis = capMillis;
    }

    /**
     * Calls the model and re-samples only a successful response with no usable content. The OpenAI SDK already retries transport failures; retrying those again here would multiply
     * one logical turn into a request storm. Returns {@code null} when the SDK call fails or both samples are empty.
     */
    @Nullable
    private ChatResponse callModel(Prompt prompt, int turn, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> stepListener,
            @Nullable GenerationActivityTracker activity) {
        String providerFailureKey = ProviderFailureCooldown.keyForModel(configuredModel());
        for (int sample = 1; sample <= EMPTY_RESPONSE_SAMPLES; sample++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            try {
                AgentPromptSafety.requirePromptSafe(prompt);
                ChatResponse response = callProvider(prompt, providerFailureKey, usageSink, activity);
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
     * Executes one admitted provider request and preserves the distinction between a local cooldown rejection and an indeterminate provider outcome. Once the supplier starts, an
     * exception cannot prove zero billable usage because the SDK/provider may already have accepted or retried the request.
     */
    @Nullable
    private ChatResponse callProvider(Prompt prompt, String providerFailureKey, @Nullable Consumer<ChatResponse> usageSink, @Nullable GenerationActivityTracker activity) {
        AtomicBoolean attempted = new AtomicBoolean();
        ChatResponse response;
        try {
            response = providerFailureCooldown.execute(providerFailureKey, providerHardFailureCooldown, () -> {
                attempted.set(true);
                return chatModel.call(prompt);
            });
        }
        catch (RuntimeException error) {
            if (attempted.get()) {
                markUsageUncertain(usageSink);
            }
            throw error;
        }
        if (response == null) {
            markUsageUncertain(usageSink);
        }
        else {
            emitUsage(usageSink, response);
            // Counted here rather than per turn: a turn can re-sample an empty response, and a local cooldown rejection never reaches the provider at all.
            if (attempted.get() && activity != null) {
                activity.recordModelCall();
            }
        }
        return response;
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
        long contextTokens = AgentConversationContext.estimateContextTokens(conversation, lastPromptTokens, messagesAtLastCall);
        if (contextTokens <= (long) contextWindowTokens - RESERVE_TOKENS) {
            return conversation;
        }
        if (cancelled.getAsBoolean()) {
            return conversation;
        }
        emit(stepListener, "Preparing the next generation step.");
        return compact(conversation, usageSink, cancelled, AgentActivitySink.trackerOf(stepListener));
    }

    private static long promptTokensOf(@Nullable ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return 0;
        }
        Number promptTokens = response.getMetadata().getUsage().getPromptTokens();
        return promptTokens == null ? 0 : promptTokens.longValue();
    }

    /**
     * Compacts the conversation: keeps the protected prefix (system prompt + initial instruction), summarizes the oldest turns into one synthetic {@link UserMessage} marked with
     * {@link #SUMMARY_SENTINEL} (so a later compaction folds it forward), and keeps the newest turns verbatim. The cut lands on a turn boundary so the result satisfies the
     * tool-pairing contract. If summarization fails, the old region is dropped behind a marker rather than aborting the run — the workspace files remain the source of truth.
     */
    List<Message> compact(List<Message> conversation, @Nullable Consumer<ChatResponse> usageSink) {
        return compact(conversation, usageSink, () -> false, null);
    }

    List<Message> compact(List<Message> conversation, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        return compact(conversation, usageSink, cancelled, null);
    }

    List<Message> compact(List<Message> conversation, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled, @Nullable GenerationActivityTracker activity) {
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
            summaryBody = summarize(toSummarize, usageSink, cancelled, activity);
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
        AgentConversationContext.assertValidPairing(rebuilt);
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
            long messageTokens = AgentConversationContext.estimateMessageTokens(conversation.get(i));
            if (tail > 0 && tail + messageTokens > KEEP_RECENT_TOKENS) {
                break;
            }
            tail += messageTokens;
            cut = i;
        }
        cut = snapToTurnStart(conversation, cut);
        long budget = (long) contextWindowTokens - RESERVE_TOKENS;
        long fixed = AgentConversationContext.estimateTokens(conversation, 0, protectedPrefix) + SUMMARY_MAX_OUTPUT_TOKENS + AgentConversationContext.MESSAGE_OVERHEAD_TOKENS;
        while (cut < n && fixed + AgentConversationContext.estimateTokens(conversation, cut, n) > budget) {
            cut = snapToTurnStart(conversation, cut + 1);
        }
        if (cut == n) {
            // Even the minimal tail does not fit, so the conversation becomes summary-only.
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
    private String summarize(List<Message> messages, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled, @Nullable GenerationActivityTracker activity) {
        StringBuilder transcript = new StringBuilder();
        for (Message message : messages) {
            transcript.append(renderForSummary(message)).append('\n');
        }
        List<Message> summaryPrompt = List.of(new SystemMessage(SUMMARIZATION_SYSTEM_PROMPT),
                new UserMessage("Summarize the following earlier session messages into the structured summary described above:\n\n" + transcript));
        // The options must be OpenAiChatOptions rather than a generic ChatOptions: OpenAiChatModel#buildRequestPrompt casts the runtime options, so a DefaultChatOptions throws
        // ClassCastException. Reasoning effort is pinned low where the provider supports it — a server-side reasoning default would otherwise consume the whole summary allowance.
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
        AgentPromptSafety.requirePromptSafe(prompt);
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Generation was cancelled before conversation compaction");
        }
        ChatResponse response = callProvider(prompt, ProviderFailureCooldown.keyForModel(configuredModel), usageSink, activity);
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

    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private static void emit(@Nullable Consumer<String> stepListener, String message) {
        AgentActivitySink.emit(stepListener, message);
    }

    /**
     * Announces that this turn's provider call is about to be issued. {@code chatModel.call(prompt)} is synchronous and routinely runs for minutes, so without this the whole
     * wall clock a run spends inside a model call is invisible and a working run is indistinguishable from a hung one.
     * <p>
     * The message is deliberately generic. Prompts, tool arguments, and workspace paths never reach it (see {@link HyperionSecretMaterialPolicy} and {@link AgentToolProgress}).
     */
    private static void emitWaitingOnModel(@Nullable Consumer<String> stepListener, @Nullable GenerationActivityTracker activity) {
        if (stepListener == null) {
            return;
        }
        if (activity != null && stepListener instanceof AgentActivitySink activitySink) {
            activitySink.activity(WAITING_ON_MODEL_MESSAGE, activity.waitingOnModel());
            return;
        }
        stepListener.accept(WAITING_ON_MODEL_MESSAGE);
    }

    private static void emitUsage(@Nullable Consumer<ChatResponse> usageSink, @Nullable ChatResponse response) {
        if (usageSink != null && response != null) {
            usageSink.accept(response);
        }
    }

    private static void markUsageUncertain(@Nullable Consumer<ChatResponse> usageSink) {
        if (usageSink instanceof ProviderUsageSink providerUsageSink) {
            providerUsageSink.markUncertain();
        }
    }

    private static void recordToolCalls(@Nullable Consumer<ChatResponse> usageSink, long count) {
        if (usageSink instanceof ProviderUsageSink providerUsageSink) {
            providerUsageSink.recordToolCalls(count);
        }
    }

    private static void recordTurn(@Nullable Consumer<ChatResponse> usageSink) {
        if (usageSink instanceof ProviderUsageSink providerUsageSink) {
            providerUsageSink.recordTurn();
        }
    }
}
