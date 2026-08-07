package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import java.util.List;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;

/**
 * The gaps between the instructor's brief and the produced tests — the quality axes the differential oracle ({@link DifferentialVerificationService}) is structurally blind to.
 * <p>
 * Contract-risk findings require instructor review after a mechanically valid candidate is saved. Presentation findings remain advisory.
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
        /** A graded test file whose assertions carry no human-readable failure message, so a failing student sees only "expected X but was Y". Advisory. */
        MISSING_FAILURE_MESSAGE,
        /** The statement, reference solution, tests, template, or worked examples make incompatible claims about observable behaviour. */
        CONTRACT_CONTRADICTION,
        /** A graded assertion or required public symbol is not discoverable from the student-facing statement and template. */
        HIDDEN_GRADED_REQUIREMENT,
        /**
         * A text-only reviewer suspects that plausible contract-breaking implementations are not distinguished by the generated assertions. Advisory until execution proves the
         * claim: an LLM hypothesis is not sufficient evidence for an autonomous repair.
         */
        WEAK_TEST_ORACLE,
        /**
         * A complete contract-breaking implementation passed the generated suite while an independently authored counterexample passed on the pristine solution and failed on
         * that implementation. Environment-proven, so it may drive an autonomous repair.
         */
        EXECUTABLE_WEAK_TEST_ORACLE,
        /**
         * Execution proved that a generated suite accepts an implementation that violates the frozen specification, but the pre-freeze review did not approve that specification.
         * The evidence is retained for the instructor without autonomously strengthening grading for a contract that may itself be wrong.
         */
        EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL,
        /**
         * The student task structure and starter scaffold disagree: provided code fails outside a student-owned seam, a required API is missing, one implementation seam is split
         * into test-shaped tasks, student work has no task/TODO anchor, stub documentation is missing or differs between solution and template, or the statement duplicates the
         * template API instead of keeping the template as the point-of-use reference.
         */
        TEMPLATE_QUALITY_GAP,
        /**
         * The specification states a rule mandating an implementation technique — that a method be recursive, use a stream pipeline, avoid loops — which behavioural tests cannot
         * observe through the public API. Blocking but not auto-repairable: only instructor review can accept the objective as ungraded or remove the mandate.
         */
        UNENFORCEABLE_TECHNIQUE_RULE,
        /**
         * An executable test for a rule of the approved specification, authored by an independent pass and proven to pass against the reference solution. Advisory: a validated
         * witness shows the test is legal and rule-derived, NOT that the graded suite lacks that coverage — establishing absence would require running it against an
         * implementation the suite accepts. Adopting it therefore either strengthens grading or is redundant, and never blocks an otherwise sound candidate.
         */
        CONTRACT_WITNESS_AVAILABLE,
        /**
         * A test passed the reference and failed the starter, but independent review could not establish that its assertion is safe to grade. The optional proposal was not
         * adopted and does not by itself prove the current exercise defective.
         */
        CONTRACT_WITNESS_ADJUDICATION_UNAVAILABLE,
        /**
         * The primary full-artifact review completed, but an auxiliary executable mutant or reference-witness check could not produce complete evidence. This blocks autonomous
         * convergence without invalidating the reviewed, mechanically verified candidate.
         */
        EXECUTABLE_EVIDENCE_UNAVAILABLE,
        /** The automated full-artifact quality review could not produce a complete verdict. */
        QUALITY_REVIEW_UNAVAILABLE,
        /** A complete pre-freeze specification review still rejected the compiled contract after its bounded refinement budget. */
        SPECIFICATION_REVIEW_FINDING,
        /**
         * A complete concept review admitted no candidate, and the run proceeded with the least-rejected one rather than producing nothing. Blocking and not auto-repairable: the
         * objection is to the exercise's central design choice, which no scoped repair of tests, scaffold, or statement can settle.
         */
        CONCEPT_ADMISSION_FINDING
    }

    /** One spec-fidelity gap: {@code requirement} names it in the instructor's own terms, {@code detail} explains what to do about it. */
    public record Finding(Kind kind, String requirement, String detail) {

        public boolean isBlocking() {
            return switch (kind) {
                case UNCOVERED_REQUIREMENT, MECHANICS_LEAK, INVENTED_REQUIREMENT, UNREQUESTED_ADAPTATION_CHANGE, REQUESTED_ADAPTATION_CHANGE_MISSING,
                        ADAPTATION_SCOPE_REVIEW_UNAVAILABLE, CONTRACT_CONTRADICTION, HIDDEN_GRADED_REQUIREMENT, EXECUTABLE_WEAK_TEST_ORACLE, TEMPLATE_QUALITY_GAP,
                        UNENFORCEABLE_TECHNIQUE_RULE, EXECUTABLE_EVIDENCE_UNAVAILABLE, QUALITY_REVIEW_UNAVAILABLE, SPECIFICATION_REVIEW_FINDING, CONCEPT_ADMISSION_FINDING ->
                    true;
                case MISSING_WORKED_EXAMPLE, MISSING_FAILURE_MESSAGE, WEAK_TEST_ORACLE, EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL, CONTRACT_WITNESS_AVAILABLE,
                        CONTRACT_WITNESS_ADJUDICATION_UNAVAILABLE ->
                    false;
            };
        }
    }

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

    public static SpecFidelityReport executableEvidenceUnavailable(String detail) {
        return new SpecFidelityReport(List.of(new Finding(Kind.EXECUTABLE_EVIDENCE_UNAVAILABLE, "Executable quality evidence could not be completed",
                detail + " Review the saved exercise carefully before releasing it.")));
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }

    public boolean hasBlockingFindings() {
        return findings.stream().anyMatch(Finding::isBlocking);
    }
}
