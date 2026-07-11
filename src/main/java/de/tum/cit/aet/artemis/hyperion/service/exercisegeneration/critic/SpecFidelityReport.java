package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import java.util.List;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;

/**
 * Result of the spec-fidelity and adaptation-scope review — quality axes the differential oracle is structurally blind to.
 * <p>
 * The differential oracle ({@link DifferentialVerificationService}) proves an exercise is internally consistent (the solution passes its own tests, the template fails them, the
 * bindings resolve) but never whether it implements the instructor's brief. This report carries the gaps between the brief and the produced tests (see {@link Kind} for the finding
 * categories).
 * <p>
 * Coverage findings remain advisory. Unrequested adaptation changes and unavailable adaptation-scope verdicts block direct live persistence and are saved for manual review
 * instead.
 *
 * @param findings the spec-fidelity or adaptation-scope findings
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
        /** An adaptation changed or removed existing content that its feedback did not request changing. */
        UNREQUESTED_ADAPTATION_CHANGE,
        /** The adaptation-scope review could not produce a trustworthy verdict. */
        ADAPTATION_SCOPE_REVIEW_UNAVAILABLE,
        /**
         * A graded test file whose assertions carry no human-readable failure message, so a failing student sees only "expected X but was Y" with no hint at which behaviour broke
         * (the gold-standard Artemis test pairs every check with a descriptive message). Deterministic, advisory.
         */
        MISSING_FAILURE_MESSAGE
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

    public static SpecFidelityReport adaptationScopeUnavailable(String detail) {
        return new SpecFidelityReport(List.of(new Finding(Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE, "Adaptation scope could not be verified",
                detail + " The generated files were kept in an isolated review draft instead of changing the live exercise.")));
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }

    public boolean hasBlockingFindings() {
        return findings.stream().anyMatch(finding -> finding.kind() == Kind.UNREQUESTED_ADAPTATION_CHANGE || finding.kind() == Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE);
    }
}
