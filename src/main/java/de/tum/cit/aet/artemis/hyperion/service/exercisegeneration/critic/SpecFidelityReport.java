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
 * Contract-risk findings require instructor review after a mechanically valid candidate is saved. Presentation findings remain advisory.
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
        /** An important, non-obvious behaviour whose contract needs a concrete input-to-outcome example to be clear. */
        MISSING_WORKED_EXAMPLE,
        /** A requirement/constraint the produced problem statement imposes that the instructor's brief never asked for (scope drift the instructor should confirm). */
        INVENTED_REQUIREMENT,
        /** An adaptation added, changed, or removed content that its feedback did not request changing. */
        UNREQUESTED_ADAPTATION_CHANGE,
        /** An adaptation omitted all or part of a change explicitly requested by its feedback. */
        REQUESTED_ADAPTATION_CHANGE_MISSING,
        /** The adaptation-scope review could not produce a complete verdict. */
        ADAPTATION_SCOPE_REVIEW_UNAVAILABLE,
        /**
         * A graded test file whose assertions carry no human-readable failure message, so a failing student sees only "expected X but was Y" with no hint at which behaviour broke
         * (the gold-standard Artemis test pairs every check with a descriptive message). Deterministic, advisory.
         */
        MISSING_FAILURE_MESSAGE,
        /** The statement, reference solution, tests, template, or worked examples make incompatible claims about observable behaviour. */
        CONTRACT_CONTRADICTION,
        /** A graded assertion or required public symbol is not discoverable from the student-facing statement and template. */
        HIDDEN_GRADED_REQUIREMENT,
        /** Plausible contract-breaking implementations are not distinguished by the generated assertions. */
        WEAK_TEST_ORACLE,
        /**
         * The starter code prevents meaningful incremental work or task-specific feedback, or ships without the house teaching scaffold: a stubbed member's doc comment does not
         * restate its student-visible contract, a statement task has no imperative TODO anchor at the place the work happens, or the solution/template diff carries non-student
         * documentation changes.
         */
        TEMPLATE_QUALITY_GAP,
        /** The automated full-artifact quality review could not produce a complete verdict. */
        QUALITY_REVIEW_UNAVAILABLE
    }

    /**
     * One spec-fidelity gap.
     *
     * @param kind        the category of this gap (see {@link Kind})
     * @param requirement the concrete requirement or the leaked phrase, in the instructor's own terms
     * @param detail      a short human-readable explanation of why this is a gap and what to do about it
     */
    public record Finding(Kind kind, String requirement, String detail) {

        public boolean isBlocking() {
            return switch (kind) {
                case UNCOVERED_REQUIREMENT, MECHANICS_LEAK, UNREQUESTED_ADAPTATION_CHANGE, REQUESTED_ADAPTATION_CHANGE_MISSING, ADAPTATION_SCOPE_REVIEW_UNAVAILABLE,
                        CONTRACT_CONTRADICTION, HIDDEN_GRADED_REQUIREMENT, WEAK_TEST_ORACLE, TEMPLATE_QUALITY_GAP, QUALITY_REVIEW_UNAVAILABLE ->
                    true;
                case INVENTED_REQUIREMENT -> true;
                case MISSING_WORKED_EXAMPLE, MISSING_FAILURE_MESSAGE -> false;
            };
        }
    }

    /** @return an empty report (no findings), used when the critic is skipped or finds nothing. */
    public static SpecFidelityReport empty() {
        return new SpecFidelityReport(List.of());
    }

    public static SpecFidelityReport adaptationScopeUnavailable(String detail) {
        return new SpecFidelityReport(List.of(new Finding(Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE, "Adaptation scope could not be verified",
                detail + " Review the saved adaptation carefully before releasing the exercise.")));
    }

    public static SpecFidelityReport qualityReviewUnavailable(String detail) {
        return new SpecFidelityReport(List
                .of(new Finding(Kind.QUALITY_REVIEW_UNAVAILABLE, "Exercise quality could not be verified", detail + " Review the saved exercise carefully before releasing it.")));
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }

    public boolean hasBlockingFindings() {
        return findings.stream().anyMatch(Finding::isBlocking);
    }
}
