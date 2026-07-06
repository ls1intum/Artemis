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
 * The single reusable Spring AI tool-calling agent loop used by the TRANSFORMING and REPAIRING phases
 * (plan Section 2.5, "Hybrid agentic core"). Written once, exercise-type-agnostic; parameterized only by
 * the type's toolset, the ChangePlan (system-prompt contract), and iteration/token budgets. ALL quality
 * investment (loop robustness, budget handling, transcript logging) lands here and benefits every type.
 *
 * One call of {@link #runLoop} is ONE outer round: Spring AI executes the internal tool-call loop within
 * the single ChatClient call; the pipeline owns the outer transform → verify → repair iteration.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class VariantAgentLoopRunner {

    private static final Logger log = LoggerFactory.getLogger(VariantAgentLoopRunner.class);

    /** Pipeline id for token-usage traces of TRANSFORMING/REPAIRING rounds (plan Section 7 telemetry). */
    static final String TRANSFORM_PIPELINE_ID = "exercise-variant-transform";

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
     * Budgets for one agent run (plan Section 2.5: iteration budget ≈ 3–5 verify cycles + token budget).
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
     *                            step output summary (plan Section 2.4)
     * @param touchedTestRepo programming only: whether the round edited the test repository — forces
     *                            re-verification of BOTH builds (plan Section 3, build-dependency constraint)
     * @param tokensUsed      tokens consumed in this round (telemetry, plan Section 7)
     */
    public record AgentResult(String finishSummary, boolean touchedTestRepo, long tokensUsed) implements Serializable {
    }

    /**
     * Runs ONE agent round with the type's toolset (plan Section 2.5, Spring AI implementation note).
     *
     * @param plan           the ChangePlan contract, rendered into the system prompt
     * @param toolset        the per-round toolset (from {@code VariantToolsetFactory.createTools}); tools observe
     *                           the job's cancel flag between invocations, and the toolset reports the round state
     *                           (finish summary, touched-test-repo flag) back to the pipeline
     * @param budgets        iteration/token budgets
     * @param job            the running job (cancellation flag, telemetry attribution)
     * @param repairFeedback null on the first round; the previous round's VerificationReport on repair rounds —
     *                           injected verbatim as the repair signal ("closed-loop repair on real signals",
     *                           plan Section 7)
     * @param promptTemplate resource path of the type's transform system prompt
     * @return the round result
     */
    public AgentResult runLoop(ChangePlan plan, VariantToolset toolset, AgentBudgets budgets, VariantJob job, @Nullable VerificationReport repairFeedback, String promptTemplate) {
        if (chatClient == null) {
            throw new IllegalStateException("AI chat client is not configured");
        }
        List<ToolCallback> tools = toolset.toolCallbacks();
        String systemPrompt = templateService.render(promptTemplate, Map.of("changePlan", renderPlanContract(plan)));
        String userMessage = repairFeedback == null ? initialUserMessage(plan) : repairUserMessage(repairFeedback);

        log.debug("Starting agent round for job {} with {} tools (repair: {})", job.getJobId(), tools.size(), repairFeedback != null);
        // Spring AI executes the tool-call loop internally within this single call; tool implementations log
        // the per-call transcript (plan Section 2.5, point 5) and observe the cancel flag between calls.
        // tools(Object...) is the unified non-deprecated API in Spring AI 2.0 and accepts ToolCallback instances.
        ChatResponse chatResponse = chatClient.prompt().system(systemPrompt).user(userMessage).tools(tools.toArray()).call().chatResponse();
        String content = LLMTokenUsageService.extractResponseText(chatResponse);
        log.debug("Agent round finished for job {}: {}", job.getJobId(), content);

        Long userId = userRepository.findOneByLogin(job.getInitiatorLogin()).map(User::getId).orElse(null);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, TRANSFORM_PIPELINE_ID,
                builder -> builder.withExercise(job.getVariantExerciseId()).withUser(userId));

        String finishSummary = toolset.finishSummary() != null ? toolset.finishSummary() : content;
        return new AgentResult(finishSummary, toolset.touchedTestRepo(), extractTotalTokens(chatResponse));
    }

    /**
     * Reads the total token count from the response metadata; 0 when the provider reported no usage.
     * NOTE: with the internal Spring AI tool-execution loop, the returned metadata reflects the final call of the
     * round — a lower bound, not the exact sum over all internal tool-call iterations. Good enough for budget
     * enforcement and the thesis telemetry (plan Section 7); exact per-iteration accounting would require
     * disabling internal tool execution.
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
        String findings = report.findings().stream().map(finding -> "[" + finding.gate() + "] " + finding.message()).collect(Collectors.joining("\n"));
        return "Verification failed with the following findings. Fix exactly these issues using your tools, keeping all invariants intact, "
                + "then call finish with a short summary. Be efficient: you have a limited tool-call budget for this round — "
                + "fix each finding once, validate once, then call finish.\n\nFindings:\n" + findings;
    }
}
