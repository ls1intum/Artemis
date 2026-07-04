package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.util.List;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * The single reusable Spring AI tool-calling agent loop used by the TRANSFORMING and REPAIRING phases
 * (plan Section 2.5, "Hybrid agentic core"). Written once, exercise-type-agnostic; parameterized only by
 * the type's toolset, the ChangePlan (system-prompt contract), and iteration/token budgets. ALL quality
 * investment (loop robustness, budget handling, transcript logging) lands here and benefits every type.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class VariantAgentLoopRunner {

    // TODO (Opus): Inject via constructor:
    // - org.springframework.ai.chat.client.ChatClient (the Hyperion ChatClient from SpringAIConfiguration.chatClient,
    // plan Section 2.5 "already in-house")
    // - HyperionPromptTemplateService (renders variants/transform_programming_system.st / transform_quiz_system.st)
    // - LLMTokenUsageService (token budget tracking, plan Sections 2.5 and 7)

    /**
     * Budgets for one agent run (plan Section 2.5: iteration budget ≈ 3–5 verify cycles + token budget).
     *
     * @param maxToolRoundsPerCall safety bound on Spring AI's internal tool-call loop per ChatClient call
     * @param tokenBudget          total token budget for the whole TRANSFORMING/REPAIRING sequence, tracked via
     *                                 LLMTokenUsageService
     */
    public record AgentBudgets(int maxToolRoundsPerCall, long tokenBudget) implements Serializable {
    }

    /**
     * Result of one agent round.
     *
     * @param finishSummary   the summary the agent passed to its finish(summary) tool — becomes the
     *                            TRANSFORMING/REPAIRING step output summary (plan Section 2.4)
     * @param touchedTestRepo programming only: whether the round edited the test repository — forces re-verification
     *                            of BOTH builds and discards prior green evidence (plan Section 3, build-dependency constraint)
     * @param tokensUsed      tokens consumed in this round (telemetry, plan Section 7)
     */
    public record AgentResult(String finishSummary, boolean touchedTestRepo, long tokensUsed) implements Serializable {
    }

    /**
     * Runs ONE agent round (one outer iteration; the pipeline owns the outer transform→verify→repair loop).
     *
     * TODO (Opus): Implement per plan Section 2.5 (Spring AI implementation note):
     * 1. Build the system prompt from the type's transform template (prompt name passed by the pipeline:
     * "variants/transform_programming_system.st" or "variants/transform_quiz_system.st") with the ChangePlan
     * rendered in as the contract (title, problem statement, intendedChanges, invariants).
     * 2. Build the user message: on the first round, the transformation instruction; on repair rounds, inject the
     * latest VerificationReport findings verbatim as the repair signal ("closed-loop repair on real signals",
     * Section 7).
     * 3. Call chatClient.prompt().system(...).user(...).toolCallbacks(tools).call() — Spring AI executes the
     * tool-call loop internally per call; this method is the "outer loop body" and must NOT loop over verify
     * cycles itself (the pipeline does, bounded by the verify-iteration budget).
     * 4. Enforce budgets: track tokens via LLMTokenUsageService; abort the round when tokenBudget is exceeded and
     * report that in the AgentResult so the pipeline can go to DRAFT_WITH_WARNINGS (Section 6, budget row).
     * 5. Transcript logging: log the full tool-call transcript (tool name, args digest, result digest) at DEBUG for
     * the thesis failure taxonomy (Section 7, point 4) — do NOT store the transcript in the Hazelcast job record.
     * 6. Cancellation: between tool calls, check job.cancelRequested (cooperative, never mid-LLM-call) and stop
     * cleanly (Section 5.2). Cancellation mid-round discards the round's work.
     * 7. Malformed tool output handling: JSON-schema/argument validation errors are returned to the model as the
     * tool result; after 2 re-prompts propagate a failure (Section 6, row 2).
     *
     * @param plan           the ChangePlan contract
     * @param tools          the type-specific tool callbacks (from VariantToolsetFactory.createTools)
     * @param budgets        iteration/token budgets
     * @param job            the running job (cancellation flag, telemetry)
     * @param repairFeedback null on the first round; the previous VerificationReport on repair rounds
     * @param promptTemplate template name for the type's transform system prompt
     * @return the round result
     */
    public AgentResult runLoop(ChangePlan plan, List<ToolCallback> tools, AgentBudgets budgets, VariantJob job, VerificationReport repairFeedback, String promptTemplate) {
        // TODO (Opus): implement — see method Javadoc.
        throw new UnsupportedOperationException("TODO (Opus): implement the generic agent loop (plan Section 2.5)");
    }
}
