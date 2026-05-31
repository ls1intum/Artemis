package de.tum.cit.aet.artemis.math.grader;

import de.tum.cit.aet.artemis.math.domain.MathNode;

/**
 * Result of validating a single proposed step without persisting it. Used for inline UX:
 * the workspace can ask the grader "is this step valid right now?" before the student commits.
 * <p>
 * Not every grader can support this — Lean / Isabelle typically grade the whole math at submit time.
 * Returned wrapped in {@code Optional} from {@link MathGrader#validateStep}.
 *
 * @param valid          whether the proposed step is structurally valid
 * @param message        rejection reason ({@code null} on success)
 * @param expectedResult tree the grader would expect to see after applying the proposed step ({@code null} on rejection)
 */
public record StepValidation(boolean valid, String message, MathNode expectedResult) {
}
