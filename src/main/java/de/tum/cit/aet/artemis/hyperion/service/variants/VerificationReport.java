package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.util.List;

/**
 * Result of one VERIFYING pass over a provisioned variant (plan Section 2.6).
 * Findings are structured data: they are (a) fed back into the agent loop as the repair signal
 * (Section 2.5), (b) recorded as the VERIFYING step output on the job (Section 2.4), and
 * (c) attached as warnings when the budget is exhausted and the job ends in DRAFT_WITH_WARNINGS
 * (Section 2.6, "Never silent success").
 *
 * @param passed   true iff ALL gates are green (programming: solution build 100% pass AND template build fails;
 *                     quiz: isValid() AND validateQuizExerciseFiles; all types: semantic consistency gate)
 * @param findings the individual findings, empty when passed
 */
public record VerificationReport(boolean passed, List<VerificationFinding> findings) implements Serializable {

    /**
     * One verifier finding.
     *
     * @param gate    which gate produced the finding, e.g. "SOLUTION_BUILD", "TEMPLATE_BUILD", "QUIZ_VALIDITY",
     *                    "CONSISTENCY" — TODO (Sonnet): turn into a small enum once the gate set is final (Section 2.6 lists
     *                    the fixed order: cheapest/most objective first)
     * @param message human-readable description that is injected verbatim into the agent's next-round user message,
     *                    e.g. compiler output excerpt, failing test name + assertion message, per-question quiz
     *                    validation error, consistency issue description (Section 6 rows 3–4)
     */
    public record VerificationFinding(String gate, String message) implements Serializable {
    }

    // TODO (Sonnet): Add a static factory `VerificationReport pass()` and a convenience
    // `String toAgentFeedback()` that renders the findings as the repair-signal block for the next agent round
    // (plan Section 2.5, "injecting the latest VerificationReport into each round's user message").
}
