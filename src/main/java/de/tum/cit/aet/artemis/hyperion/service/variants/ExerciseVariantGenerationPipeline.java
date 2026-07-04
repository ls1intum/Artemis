package de.tum.cit.aet.artemis.hyperion.service.variants;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Drives one variant job through the explicit phase state machine (plan Sections 2.1, 2.2, 2.7.2).
 * Type-agnostic: everything type-specific is resolved via {@link VariantTypeRegistry} into the five
 * capability adapters (Section 2.3). NOT called "orchestrator" anywhere — see the naming note in Section 2.1.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseVariantGenerationPipeline {

    // TODO (Opus): Inject via constructor:
    // - VariantTypeRegistry (adapter resolution)
    // - VariantAgentLoopRunner (TRANSFORMING/REPAIRING rounds)
    // - ExerciseVariantJobService (phase updates, step outputs, cancel-flag reads, event publishing — the job
    // service is the single writer to the Hazelcast record and the websocket topic)
    // - HyperionPromptTemplateService + ChatClient for the PLANNING call (BeanOutputConverter<ChangePlan>),
    // prompt "variants/plan_programming.st" / "variants/plan_quiz.st" selected by exercise type (Sections 3/4)
    // - the exercise repositories needed to reload the source/variant entities inside the async thread
    // - the existing exercise deletion service used for cleanup on FAILED/CANCELLED (Section 6)

    /**
     * Runs the whole pipeline for one job. Called only from ExerciseVariantTaskService.runJobAsync (@Async).
     *
     * TODO (Opus): Implement the phase machine exactly as the state diagram in plan Section 2.7.2:
     *
     * 1. ANALYZING: resolve adapters via registry; render source context via VariantContextRenderer.renderContext;
     * record a StepOutput; publish PHASE_CHANGED.
     * 2. PLANNING: one structured LLM call (BeanOutputConverter<ChangePlan>) with rendered context + wizard intent
     * (targetDifficulty/domainText/additionalInstructions) per Section 2.4. Malformed output → return the
     * conversion error to the model, max 2 re-prompts, then FAILED (Section 6 row 2). Store the ChangePlan on
     * the job; record the rendered plan as the PLANNING step output (expandable panel, Section 2.4).
     * The planner prompt must bias toward plans that keep the test surface stable when the intent allows it
     * (domain re-theme ⇒ rename-only test changes, Section 3 "Transformation order").
     * 3. PROVISIONING: ExerciseProvisioner.provision(...); store variantExerciseId on the job. On exception:
     * delete any half-created exercise via the existing deletion service, job → FAILED (Section 6).
     * 4. TRANSFORM/VERIFY/REPAIR loop, bounded by the verify-iteration budget (≈3–5, Section 2.5):
     * a. TRANSFORMING/REPAIRING: VariantAgentLoopRunner.runLoop(plan, toolsetFactory.createTools(variant, job),
     * budgets, job, lastReport, promptTemplate). Record per-attempt transform summary + diff-of-record as
     * step output; publish ATTEMPT events ("attempt 2/3", Section 5.2).
     * For programming, honor the transformation-order policy: solution repo first (until green), then test
     * repo only if the plan changes tests (then re-verify solution), then template (must fail), problem
     * statement last (Section 3). If AgentResult.touchedTestRepo: discard prior green evidence from the
     * agent context and verifier state, re-verify BOTH builds (Section 3, build-dependency constraint).
     * b. VERIFYING: VariantVerifier.verify(variant, plan). All gates green → FINALIZING. Red and attempts
     * remain → REPAIRING with the report as feedback. Budget exhausted → DRAFT_WITH_WARNINGS: keep the
     * variant as a flagged draft with the findings attached — never silently delete, never silently
     * publish (Sections 1 and 2.6).
     * 5. FINALIZING: VariantFinalizer.finalizeVariant(variant, request); then job → COMPLETED (or
     * DRAFT_WITH_WARNINGS), publish DONE with variantExerciseId (Sections 3/4 FINALIZING rows).
     * 6. Cooperative cancellation (Sections 5.2 and 2.7.2 footnote): check job.cancelRequested at EVERY phase
     * transition and between agent rounds — never mid-LLM-call or mid-build (those complete and are discarded).
     * On cancel before FINALIZING: delete the provisioned clone via the existing exercise deletion service
     * (same path as hard failure), job → CANCELLED, publish CANCELLED event. From FINALIZING on, cancellation
     * is impossible (the resource already rejects it with 409).
     * 7. Telemetry per phase: tokens (LLMTokenUsageService), attempts, wall time — for the thesis evaluation
     * (Section 7).
     *
     * @param job the claimed job (already stored in the Hazelcast map by ExerciseVariantJobService)
     */
    public void run(VariantJob job) {
        // TODO (Opus): implement — see method Javadoc and plan Section 2.7.2 state diagram.
        throw new UnsupportedOperationException("TODO (Opus): implement the phase state machine (plan Section 2.2)");
    }
}
