package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Capability interface: deterministic + semantic verification of a transformed variant during VERIFYING
 * (plan Sections 2.3 and 2.6). Gates run in fixed order, cheapest and most objective first; the model can
 * never talk its way past them (Section 7).
 */
public interface VariantVerifier {

    /**
     * Runs the full verification chain for the variant.
     *
     * TODO (Opus, programming — plan Section 2.6 step 1 + Section 3 VERIFYING row): gate 1 = solution build must
     * pass 100% of tests AND template build must fail — reuse the waitForBuildResult / hasReachedTargetResult /
     * BuildResultOutcome semantics per RepositoryType from the extracted HyperionBuildVerificationService (these
     * already encode exactly this rule). CI timeout counts as a failed attempt with a distinct detail, reusing
     * BuildResultState.TIMED_OUT semantics (Section 6, last row). Respect the build-dependency constraint: if the
     * test repo changed since the last verify, discard cached results and re-verify BOTH builds (Section 3).
     *
     * TODO (Sonnet, quiz — plan Section 2.6 step 2 + Section 4 VERIFYING row): gate = QuizExercise.isValid() with
     * per-question error details + QuizExerciseService.validateQuizExerciseFiles (structural correctness incl. DnD
     * file references). Optionally an LLM self-critique pass reusing the existing quiz refinement prompts
     * ("is the distractor set plausible? is exactly the requested change applied?").
     *
     * TODO (Sonnet, all types — plan Section 2.6 step 3): final semantic gate = consistency check between problem
     * statement and artifacts, reusing HyperionConsistencyCheckService (structural + semantic checks already
     * implemented for programming). Check the ChangePlan invariants explicitly (e.g. "test names referenced in the
     * problem statement exist in the test repo").
     *
     * @param variant the transformed variant
     * @param plan    the ChangePlan whose invariants the semantic gate checks
     * @return structured report; findings feed the agent loop as the repair signal, or the warnings list on
     *         DRAFT_WITH_WARNINGS (never silent success, Section 2.6)
     */
    VerificationReport verify(Exercise variant, ChangePlan plan);
}
