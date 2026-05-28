package de.tum.cit.aet.artemis.proof.grader;

import java.util.List;
import java.util.Optional;

import de.tum.cit.aet.artemis.proof.domain.DerivationStep;
import de.tum.cit.aet.artemis.proof.domain.MathNode;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;

/**
 * Strategy interface implemented by every proof-grading backend.
 * <p>
 * The current step-by-step engine is exposed as {@code RewriteChainGrader}.
 * Future M3 graders (Lean, Isabelle, egg e-graphs) plug in by adding a new Spring bean
 * with the appropriate {@link GraderType}; the dispatcher in
 * {@code ProofGradingService} routes per-exercise.
 * <p>
 * Remote graders are first-class citizens: an implementation may forward
 * {@link #grade} to an out-of-process service (HTTP, gRPC, FFI) — the interface
 * stays in-process from the dispatcher's POV.
 */
public interface ProofGrader {

    /**
     * @return the discriminator used to register and look up this grader
     */
    GraderType getType();

    /**
     * Grade an entire submission and return the score plus per-step status.
     *
     * @param exercise   the exercise being graded
     * @param submission the student's submission
     * @return a {@link GradingResult} with score in [0, 100] and per-step status
     */
    GradingResult grade(ProofExercise exercise, ProofSubmission submission);

    /**
     * Validate a single proposed step against the current proof state, without persisting.
     * Optional — not every grader can answer this incrementally.
     *
     * @param exercise     the exercise being worked on
     * @param currentState the proof state immediately before the proposed step
     * @param proposedStep the step the student is about to add
     * @return validation result, or empty if this grader does not support incremental validation
     */
    default Optional<StepValidation> validateStep(ProofExercise exercise, MathNode currentState, DerivationStep proposedStep) {
        return Optional.empty();
    }

    /**
     * Suggest possible next steps the student could take from the current state.
     * Optional — used to power the "Hint" button in the workspace.
     *
     * @param exercise     the exercise being worked on
     * @param currentState the student's current proof state
     * @return up to a handful of {@link HintSuggestion}s ranked by usefulness, or empty
     */
    default List<HintSuggestion> suggestHints(ProofExercise exercise, MathNode currentState) {
        return List.of();
    }

    /**
     * Run an automated reachability check from the exercise's starting expression toward its target.
     * Optional — different graders implement this differently (rewrite-chain runs a reduction strategy;
     * Lean would run {@code simp} / {@code auto}). Empty when the grader cannot answer the question.
     *
     * @param exercise the exercise to analyse
     * @return a {@link ReachabilityReport}, or empty if this grader does not support reachability checks
     */
    default Optional<ReachabilityReport> verifyReachability(ProofExercise exercise) {
        return Optional.empty();
    }
}
