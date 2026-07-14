package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;

/**
 * The single reusable Spring AI tool-calling agent loop used by the TRANSFORMING and REPAIRING phases.
 * Written once, exercise-type-agnostic; parameterized only by the type's toolset, the ChangePlan
 * (system-prompt contract), and iteration/token budgets. ALL quality investment (loop robustness, budget
 * handling, transcript logging) lands here and benefits every type.
 *
 * One call of {@link #runLoop} is ONE outer round: Spring AI executes the internal tool-call loop within
 * the single ChatClient call; the pipeline owns the outer transform → verify → repair iteration.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class VariantAgentLoopRunner {

    private static final Logger log = LoggerFactory.getLogger(VariantAgentLoopRunner.class);

    /** Pipeline id for token-usage traces of TRANSFORMING/REPAIRING rounds. */
    static final String TRANSFORM_PIPELINE_ID = "exercise-variant-transform";

    /**
     * Message of the {@link IllegalStateException} Spring AI's DefaultToolCallingManager throws when the model
     * requests a tool that is not registered (a hallucinated tool name). Unlike errors inside an existing tool —
     * which are fed back to the model as the tool result — this aborts the whole ChatClient call.
     */
    private static final String UNKNOWN_TOOL_MESSAGE = "No ToolCallback found for tool name";

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    @Nullable
    private final ChatClient chatClient;

    public VariantAgentLoopRunner(HyperionPromptTemplateService templateService, LLMTokenUsageService llmTokenUsageService, UserRepository userRepository,
            @Nullable ChatClient chatClient) {
        this.templateService = templateService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
        this.chatClient = chatClient;
    }

    /**
     * Budgets for one agent run: iteration budget (≈ 3–5 verify cycles) + token budget.
     *
     * @param maxVerifyAttempts outer verify-cycle budget (enforced by the pipeline, carried here for prompt context)
     * @param tokenBudget       total token budget for the whole TRANSFORMING/REPAIRING sequence
     */
    public record AgentBudgets(int maxVerifyAttempts, long tokenBudget) implements Serializable {
    }

    /**
     * Result of one agent round.
     *
     * @param finishSummary   the model's final text output for the round — becomes the TRANSFORMING/REPAIRING
     *                            step output summary
     * @param touchedTestRepo programming only: whether the round edited the test repository — forces
     *                            re-verification of BOTH builds (build-dependency constraint)
     * @param tokensUsed      tokens consumed in this round (telemetry)
     */
    public record AgentResult(String finishSummary, boolean touchedTestRepo, long tokensUsed) implements Serializable {
    }

    /**
     * Runs ONE agent round with the type's toolset.
     *
     * @param plan           the ChangePlan contract, rendered into the system prompt
     * @param toolset        the per-round toolset (from {@code VariantToolsetFactory.createTools}); tools observe
     *                           the job's cancel flag between invocations, and the toolset reports the round state
     *                           (finish summary, touched-test-repo flag) back to the pipeline
     * @param budgets        iteration/token budgets
     * @param job            the running job (cancellation flag, telemetry attribution)
     * @param repairFeedback null on the first round; the previous round's VerificationReport on repair rounds —
     *                           injected verbatim as the repair signal (closed-loop repair on real signals)
     * @param promptTemplate resource path of the type's transform system prompt
     * @return the round result
     */
    public AgentResult runLoop(ChangePlan plan, VariantToolset toolset, AgentBudgets budgets, VariantJob job, @Nullable VerificationReport repairFeedback, String promptTemplate) {
        if (chatClient == null) {
            throw new IllegalStateException("AI chat client is not configured");
        }
        List<ToolCallback> tools = toolset.toolCallbacks();
        // The tool catalog is rendered from the actual ToolCallbacks, so a tool added to a toolset is
        // automatically part of the prompt's "these are the ONLY tools" contract — no manual prompt upkeep.
        String systemPrompt = templateService.render(promptTemplate, Map.of("changePlan", renderPlanContract(plan), "availableTools", renderToolCatalog(tools)));
        String userMessage = repairFeedback == null ? initialUserMessage(plan) : repairUserMessage(repairFeedback);

        log.debug("Starting agent round for job {} with {} tools (repair: {})", job.getJobId(), tools.size(), repairFeedback != null);
        ChatResponse chatResponse = callAgent(job, systemPrompt, userMessage, tools);
        String content = LLMTokenUsageService.extractResponseText(chatResponse);
        log.debug("Agent round finished for job {}: {}", job.getJobId(), content);
        // Round boundary: persist whatever the round left unpersisted (e.g. repo edits without a final runBuild)
        // BEFORE reading the round state — verification must judge the round's actual work, and a flush of the
        // test repository must set the touched-test-repo flag.
        toolset.flushPendingChanges();

        Long userId = userRepository.findOneByLogin(job.getInitiatorLogin()).map(User::getId).orElse(null);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, TRANSFORM_PIPELINE_ID,
                builder -> builder.withExercise(job.getVariantExerciseId()).withUser(userId));

        String finishSummary = toolset.finishSummary() != null ? toolset.finishSummary() : content;
        return new AgentResult(finishSummary, toolset.touchedTestRepo(), extractTotalTokens(chatResponse));
    }

    /**
     * One ChatClient call with a single retry when the model hallucinates a tool name: Spring AI's internal tool
     * loop aborts the whole call (see {@link #UNKNOWN_TOOL_MESSAGE}) instead of feeding an error back to the model,
     * so without the retry one stochastic hallucination fails the entire round. The retry restarts the round with
     * the same toolset; edits already made persist in the variant, and the fresh round reads the current state.
     *
     * Spring AI executes the tool-call loop internally within this single call; tool implementations log the
     * per-call transcript and observe the cancel flag between calls.
     * tools(Object...) is the unified non-deprecated API in Spring AI 2.0 and accepts ToolCallback instances.
     */
    private ChatResponse callAgent(VariantJob job, String systemPrompt, String userMessage, List<ToolCallback> tools) {
        try {
            return chatClient.prompt().system(systemPrompt).user(userMessage).tools(tools.toArray()).call().chatResponse();
        }
        catch (IllegalStateException e) {
            if (e.getMessage() == null || !e.getMessage().contains(UNKNOWN_TOOL_MESSAGE)) {
                throw e;
            }
            log.warn("Agent round for job {} aborted by a hallucinated tool call ({}); retrying the round once", job.getJobId(), e.getMessage());
            return chatClient.prompt().system(systemPrompt).user(userMessage).tools(tools.toArray()).call().chatResponse();
        }
    }

    /**
     * Renders the comma-separated names of the round's tools for the system prompt's tool contract.
     */
    private static String renderToolCatalog(List<ToolCallback> tools) {
        return tools.stream().map(tool -> tool.getToolDefinition().name()).collect(Collectors.joining(", "));
    }

    /**
     * Reads the total token count from the response metadata; 0 when the provider reported no usage.
     * NOTE: with the internal Spring AI tool-execution loop, the returned metadata reflects the final call of the
     * round — a lower bound, not the exact sum over all internal tool-call iterations. Good enough for budget
     * enforcement and telemetry; exact per-iteration accounting would require disabling internal tool execution.
     */
    private static long extractTotalTokens(@Nullable ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return 0;
        }
        Integer totalTokens = chatResponse.getMetadata().getUsage().getTotalTokens();
        return totalTokens != null ? totalTokens : 0;
    }

    private String renderPlanContract(ChangePlan plan) {
        return "Variant title: " + plan.variantTitle() + "\n\nTarget problem statement:\n" + plan.problemStatement() + "\n\nIntended changes (apply exactly these):\n"
                + plan.intendedChanges().stream().map(change -> "- " + change).collect(Collectors.joining("\n")) + "\n\nInvariants (must be preserved):\n"
                + plan.invariants().stream().map(invariant -> "- " + invariant).collect(Collectors.joining("\n"));
    }

    private String initialUserMessage(ChangePlan plan) {
        return "Apply the change plan to the exercise copy now using your tools. Work through the intended changes in order, "
                + "verify your work with the available validation/build tools, and call finish with a short summary when done. " + "There are " + plan.intendedChanges().size()
                + " intended change(s). Be efficient: you have a limited tool-call budget for this round — read the current state once, "
                + "apply each change once, validate once at the end, and then call finish. Do NOT re-read or re-validate after every single edit.";
    }

    private String repairUserMessage(VerificationReport report) {
        String findings = report.toAgentFeedback();
        return "Verification failed with the following findings. Fix exactly these issues using your tools, keeping all invariants intact, "
                + "then call finish with a short summary. Be efficient: you have a limited tool-call budget for this round — "
                + "fix each finding once, validate once, then call finish.\n\nFindings:\n" + findings;
    }
}
