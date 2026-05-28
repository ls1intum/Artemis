package de.tum.cit.aet.artemis.proof.grader;

import java.util.List;

/**
 * Result of grading a whole submission. Surfaced back to the resource layer for persistence
 * and to the client as part of the submission response.
 *
 * @param score        final score in [0, 100]
 * @param stepStatuses per-step status, in submission order
 * @param message      optional grader-specific narrative ({@code null} if none)
 */
public record GradingResult(double score, List<StepStatus> stepStatuses, String message) {

    public GradingResult {
        stepStatuses = stepStatuses == null ? List.of() : List.copyOf(stepStatuses);
    }

    public static GradingResult of(double score) {
        return new GradingResult(score, List.of(), null);
    }
}
