package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
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
        /** Programming: every test referenced in the problem statement's task markers must exist as a real test case. */
        TEST_REFERENCES,
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

        /**
         * Digit runs (line numbers, timestamps, container/job IDs, test counts) vary between otherwise-identical
         * build logs across rounds and would defeat signature matching if left in.
         */
        private static final Pattern DIGITS = Pattern.compile("\\d+");

        /**
         * Long enough to keep the actual failure line (which sits at the end of a build log — see
         * {@code VariantBuildVerificationService.extractBuildLogs}, which keeps only the tail for the same
         * reason) while bounding how much of a long log this fingerprint depends on.
         */
        private static final int SIGNATURE_TAIL_LENGTH = 400;

        /**
         * A crude but round-to-round-stable fingerprint of this finding, used to detect a repair round stuck on
         * the same underlying problem: {@link #gate()} plus the TAIL of {@link #message()} with all digit runs
         * collapsed. A missed match only means a stuck loop goes undetected one round longer, never a false
         * escalation — it does not need to be precise, only stable across rounds when the underlying problem
         * genuinely hasn't changed.
         */
        String stableSignature() {
            String normalized = DIGITS.matcher(message() == null ? "" : message()).replaceAll("#").replaceAll("\\s+", " ").strip();
            String tail = normalized.length() > SIGNATURE_TAIL_LENGTH ? normalized.substring(normalized.length() - SIGNATURE_TAIL_LENGTH) : normalized;
            return gate() + "|" + tail;
        }
    }

    /**
     * Renders the findings as the repair-signal block injected into the agent's next-round user message.
     *
     * @return one {@code [GATE] message} line per finding, newline-separated (empty string when there are none)
     */
    public String toAgentFeedback() {
        return findings.stream().map(finding -> "[" + finding.gate() + "] " + finding.message()).collect(Collectors.joining("\n"));
    }

    /**
     * The stable signatures of every finding in this report, for stuck-repair-loop detection across rounds (see
     * {@code ExerciseVariantGenerationPipelineService.transformAndVerify}).
     *
     * @return one signature per finding, empty when the report passed
     */
    Set<String> findingSignatures() {
        return findings.stream().map(VerificationFinding::stableSignature).collect(Collectors.toSet());
    }
}
