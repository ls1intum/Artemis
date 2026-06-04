package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.List;

/**
 * Advisory result of the spec-fidelity / coverage critic — the one quality axis the differential oracle is structurally blind to.
 * <p>
 * The differential oracle ({@link AuthoritativeVerificationService}) proves an exercise is internally consistent (the solution passes its own tests, the template fails them, the
 * bindings resolve) but NEVER whether it implements the instructor's brief. This report carries the gaps between the brief and the produced tests: concrete requirements or
 * edge-cases the brief names that no test references, and grader-mechanics phrases that leaked into the student-facing problem statement.
 * <p>
 * <strong>It is purely advisory.</strong> It is NEVER consulted by the acceptance decision: an exercise the oracle accepts stays accepted regardless of what the critic finds. Its
 * findings are used in two non-blocking ways — folded into the verifier-feedback retry prompt while attempts remain (so the agent can add the missing test), and surfaced as
 * advisory review comments on the final exercise otherwise.
 *
 * @param findings the spec-fidelity gaps found (empty when the critic found nothing, the brief was trivial, or the critic itself failed and was skipped)
 */
public record SpecFidelityReport(List<Finding> findings) {

    /** Where a finding came from, so it can be phrased and weighted appropriately for the instructor and the retry prompt. */
    public enum Kind {
        /** A concrete requirement / edge-case the brief names (e.g. "CJK", "throws on zero capacity") that NO test references. */
        UNCOVERED_REQUIREMENT,
        /** A grader-mechanics phrase ("make the tests fail", "NotImplementedError in the template") that leaked into the student-facing problem statement. */
        MECHANICS_LEAK
    }

    /**
     * One spec-fidelity gap.
     *
     * @param kind        whether this is an uncovered brief requirement or a grader-mechanics leak
     * @param requirement the concrete requirement or the leaked phrase, in the instructor's own terms
     * @param detail      a short human-readable explanation of why this is a gap and what to do about it
     */
    public record Finding(Kind kind, String requirement, String detail) {
    }

    /** @return an empty report (no findings), used when the critic is skipped or finds nothing. */
    public static SpecFidelityReport empty() {
        return new SpecFidelityReport(List.of());
    }

    /** @return {@code true} when there is at least one finding to surface. */
    public boolean hasFindings() {
        return !findings.isEmpty();
    }
}
