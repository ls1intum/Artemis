package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

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

    private final HyperionPromptTemplateService templateService;

    @Nullable
    private final ChatClient chatClient;

    public VariantAgentLoopRunner(HyperionPromptTemplateService templateService, @Nullable ChatClient chatClient) {
        this.templateService = templateService;
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
     * @param tools          the type-specific tool callbacks (from {@code VariantToolsetFactory.createTools});
     *                           tools themselves check the job's cancel flag between invocations
     * @param budgets        iteration/token budgets
     * @param job            the running job (cancellation flag, telemetry attribution)
     * @param repairFeedback null on the first round; the previous round's VerificationReport on repair rounds —
     *                           injected verbatim as the repair signal ("closed-loop repair on real signals",
     *                           plan Section 7)
     * @param promptTemplate resource path of the type's transform system prompt
     * @return the round result
     */
    public AgentResult runLoop(ChangePlan plan, List<ToolCallback> tools, AgentBudgets budgets, VariantJob job, @Nullable VerificationReport repairFeedback,
            String promptTemplate) {
        if (chatClient == null) {
            throw new IllegalStateException("AI chat client is not configured");
        }
        String systemPrompt = templateService.render(promptTemplate, Map.of("changePlan", renderPlanContract(plan)));
        String userMessage = repairFeedback == null ? initialUserMessage(plan) : repairUserMessage(repairFeedback);

        log.debug("Starting agent round for job {} with {} tools (repair: {})", job.getJobId(), tools.size(), repairFeedback != null);
        // Spring AI executes the tool-call loop internally within this single call; tool implementations log
        // the per-call transcript (plan Section 2.5, point 5) and observe the cancel flag between calls.
        // tools(Object...) is the unified non-deprecated API in Spring AI 2.0 and accepts ToolCallback instances.
        String content = chatClient.prompt().system(systemPrompt).user(userMessage).tools(tools.toArray()).call().content();
        log.debug("Agent round finished for job {}: {}", job.getJobId(), content);

        // TODO (Sonnet): token accounting via LLMTokenUsageService — read usage from the ChatResponse metadata
        // (use .call().chatResponse() instead of .content()) and enforce budgets.tokenBudget across rounds by
        // accumulating on the job (plan Sections 2.5 and 7). Until then tokensUsed is reported as 0.
        // TODO (Sonnet): touchedTestRepo must be reported by the programming toolset (it observes applyEdit/writeFile
        // targets); surface it via a mutable tool-state object shared between the toolset factory and this runner,
        // then re-verify BOTH builds in the verifier (plan Section 3, build-dependency constraint). Until the
        // programming toolset exists, false is correct for quiz and a known gap for programming.
        return new AgentResult(content, false, 0);
    }

    private String renderPlanContract(ChangePlan plan) {
        return "Variant title: " + plan.variantTitle() + "\n\nTarget problem statement:\n" + plan.problemStatement() + "\n\nIntended changes (apply exactly these):\n"
                + plan.intendedChanges().stream().map(change -> "- " + change).collect(Collectors.joining("\n")) + "\n\nInvariants (must be preserved):\n"
                + plan.invariants().stream().map(invariant -> "- " + invariant).collect(Collectors.joining("\n"));
    }

    private String initialUserMessage(ChangePlan plan) {
        return "Apply the change plan to the exercise copy now using your tools. Work through the intended changes in order, "
                + "verify your work with the available validation/build tools, and call finish with a short summary when done. " + "There are " + plan.intendedChanges().size()
                + " intended change(s).";
    }

    private String repairUserMessage(VerificationReport report) {
        String findings = report.findings().stream().map(finding -> "[" + finding.gate() + "] " + finding.message()).collect(Collectors.joining("\n"));
        return "Verification failed with the following findings. Fix exactly these issues using your tools, keeping all invariants intact, "
                + "then call finish with a short summary.\n\nFindings:\n" + findings;
    }
}
