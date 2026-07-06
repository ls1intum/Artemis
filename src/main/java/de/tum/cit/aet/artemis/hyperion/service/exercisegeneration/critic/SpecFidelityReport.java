package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;

/**
 * Advisory result of the spec-fidelity / coverage critic — the one quality axis the differential oracle is structurally blind to.
 * <p>
 * The differential oracle ({@link DifferentialVerificationService}) proves an exercise is internally consistent (the solution passes its own tests, the template fails them, the
 * bindings resolve) but never whether it implements the instructor's brief. This report carries the gaps between the brief and the produced tests (see {@link Kind} for the finding
 * categories).
 * <p>
 * It is advisory: never consulted by the acceptance decision, so an exercise the oracle accepts stays accepted regardless of what the critic finds. Its findings are used in two
 * non-blocking ways — folded into the verifier-feedback retry prompt while attempts remain, and surfaced as advisory review comments on the final exercise otherwise.
 *
 * @param findings the spec-fidelity gaps found (empty when the critic found nothing, the brief was trivial, or the critic itself failed and was skipped)
 */
public record SpecFidelityReport(List<Finding> findings) {

    /** Where a finding came from, so it can be phrased and weighted appropriately for the instructor and the retry prompt. */
    public enum Kind {
        /** A concrete requirement / edge-case the brief names (e.g. "CJK", "throws on zero capacity") that no test references. */
        UNCOVERED_REQUIREMENT,
        /** A grader-mechanics phrase ("make the tests fail", "NotImplementedError in the template") that leaked into the student-facing problem statement. */
        MECHANICS_LEAK,
        /**
         * A [task] that names an error/edge behaviour but whose statement gives no concrete fenced call→result worked-example trace for it (a hand-authored exercise always does).
         */
        MISSING_WORKED_EXAMPLE,
        /** A requirement/constraint the produced problem statement imposes that the instructor's brief never asked for (scope drift the instructor should confirm). */
        INVENTED_REQUIREMENT,
        /**
         * A graded test file whose assertions carry no human-readable failure message, so a failing student sees only "expected X but was Y" with no hint at which behaviour broke
         * (the gold-standard Artemis test pairs every check with a descriptive message). Deterministic, advisory.
         */
        MISSING_FAILURE_MESSAGE,
        /**
         * The reference solution fails a test authored by an independent examiner (the decorrelated test-author agent) from the problem statement's own stated contract, so the
         * solution contradicts its own stated behaviour — an unambiguous correctness defect the same-author differential oracle is blind to (the co-authored tests encode the same
         * wrong model). Produced by the cross-check; advisory by default, hard-gated only behind the {@code reject-on-contradiction} flag.
         */
        CONTRACT_CONTRADICTION
    }

    /**
     * One spec-fidelity gap.
     *
     * @param kind        the category of this gap (see {@link Kind})
     * @param requirement the concrete requirement or the leaked phrase, in the instructor's own terms
     * @param detail      a short human-readable explanation of why this is a gap and what to do about it
     */
    public record Finding(Kind kind, String requirement, String detail) {
    }

    /** @return an empty report (no findings), used when the critic is skipped or finds nothing. */
    public static SpecFidelityReport empty() {
        return new SpecFidelityReport(List.of());
    }

    /**
     * Returns a new report with {@code finding} appended, leaving this one unchanged (the record is immutable). Used to fold a cross-check contradiction into the
     * advisory report that already rides the generation outcome, so it flows through every existing advisory surface (retry prompt, review comments) without new plumbing.
     *
     * @param finding the advisory finding to append
     * @return a new report carrying this report's findings plus {@code finding}
     */
    public SpecFidelityReport withFinding(Finding finding) {
        List<Finding> combined = new ArrayList<>(findings);
        combined.add(finding);
        return new SpecFidelityReport(List.copyOf(combined));
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }
}
