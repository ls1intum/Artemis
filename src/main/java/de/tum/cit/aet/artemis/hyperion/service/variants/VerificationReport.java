package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of one VERIFYING pass over a provisioned variant. Findings are structured data: they are (a) fed back
 * into the agent loop as the repair signal, (b) recorded as the VERIFYING step output on the job, and
 * (c) attached as warnings when the budget is exhausted and the job ends in DRAFT_WITH_WARNINGS.
 *
 * @param passed   true iff ALL gates are green (programming: solution build 100% pass AND template build fails;
 *                     quiz: isValid() AND validateQuizExerciseFiles; all types: semantic consistency gate)
 * @param findings the individual findings, empty when passed
 */
public record VerificationReport(boolean passed, List<VerificationFinding> findings) implements Serializable {

    /**
     * The verification gates, in the fixed order they run (cheapest / most objective first).
     */
    public enum VerificationGate {
        /** Programming: solution repository build must compile and pass 100% of tests. */
        SOLUTION_BUILD,
        /** Programming: template repository build must run at least one test and score 0%. */
        TEMPLATE_BUILD,
        /** Quiz: QuizExercise.isValid() (per-question and exercise-level structural validity). */
        QUIZ_VALIDITY,
        /** Quiz: validateQuizExerciseFiles (drag-and-drop file references). */
        QUIZ_FILES,
        /** Quiz: LLM self-critique soft gate ("is the requested change applied, are distractors plausible?"). */
        QUIZ_CRITIQUE,
        /** All types: semantic consistency between the problem statement and the artifacts. */
        CONSISTENCY,
        /** The token budget was exhausted before the gates went green. */
        TOKEN_BUDGET
    }

    /**
     * One verifier finding.
     *
     * @param gate    which gate produced the finding
     * @param message human-readable description that is injected verbatim into the agent's next-round user message,
     *                    e.g. compiler output excerpt, failing test name + assertion message, per-question quiz
     *                    validation error, consistency issue description
     */
    public record VerificationFinding(VerificationGate gate, String message) implements Serializable {
    }

    /**
     * Renders the findings as the repair-signal block injected into the agent's next-round user message.
     *
     * @return one {@code [GATE] message} line per finding, newline-separated (empty string when there are none)
     */
    public String toAgentFeedback() {
        return findings.stream().map(finding -> "[" + finding.gate() + "] " + finding.message()).collect(Collectors.joining("\n"));
    }
}
