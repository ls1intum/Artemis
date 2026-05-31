package de.tum.cit.aet.artemis.math.grader;

import java.util.List;
import java.util.Optional;

import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;

/**
 * Strategy interface implemented by every math-grading backend.
 * <p>
 * The current step-by-step engine is exposed as {@code RewriteChainGrader}.
 * Future M3 graders (Lean, Isabelle, egg e-graphs) plug in by adding a new Spring bean
 * with the appropriate {@link GraderType}; the dispatcher in
 * {@code MathGradingService} routes per-exercise.
 * <p>
 * Remote graders are first-class citizens: an implementation may forward
 * {@link #grade} to an out-of-process service (HTTP, gRPC, FFI) — the interface
 * stays in-process from the dispatcher's POV.
 */
public interface MathGrader {

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
    GradingResult grade(MathExercise exercise, MathSubmission submission);

    /**
     * Validate a single proposed step against the current math state, without persisting.
     * Optional — not every grader can answer this incrementally.
     *
     * @param exercise     the exercise being worked on
     * @param currentState the math state immediately before the proposed step
     * @param proposedStep the step the student is about to add
     * @return validation result, or empty if this grader does not support incremental validation
     */
    default Optional<StepValidation> validateStep(MathExercise exercise, MathNode currentState, DerivationStep proposedStep) {
        return Optional.empty();
    }

    /**
     * Suggest possible next steps the student could take from the current state.
     * Optional — used to power the "Hint" button in the workspace.
     *
     * @param exercise     the exercise being worked on
     * @param currentState the student's current math state
     * @return up to a handful of {@link HintSuggestion}s ranked by usefulness, or empty
     */
    default List<HintSuggestion> suggestHints(MathExercise exercise, MathNode currentState) {
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
    default Optional<ReachabilityReport> verifyReachability(MathExercise exercise) {
        return Optional.empty();
    }
}
