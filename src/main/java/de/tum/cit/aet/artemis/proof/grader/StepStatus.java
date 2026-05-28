package de.tum.cit.aet.artemis.proof.grader;

/**
 * Per-step status returned alongside a {@link GradingResult}.
 *
 * @param stepIndex the step's index in the submission
 * @param valid     whether the grader accepted this step
 * @param message   reason for rejection ({@code null} if {@code valid})
 */
public record StepStatus(int stepIndex, boolean valid, String message) {
}
