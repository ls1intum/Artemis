package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;

/**
 * Result of the DECORRELATED correctness cross-check: an INDEPENDENTLY-authored test suite (written by a separate examiner agent from the problem statement's stated contract,
 * never
 * from the reference solution) run against the REAL solution and template through the SAME production build+parse path the differential oracle uses.
 * <p>
 * The differential oracle ({@link AuthoritativeVerificationService}) relates four artifacts the SAME agent authored (solution/template/tests/problem-statement), so a wrong model
 * encoded consistently across the solution AND its co-authored tests is invisible to it by construction (the checked-in {@code realistic-pasted-lru} false-accept). This report
 * breaks that correlation: a solution that FAILS a test derived only from its own stated contract is contradicting its own statement — an unambiguous correctness defect.
 * <p>
 * <strong>Additive and advisory by default.</strong> It NEVER loosens the oracle's {@code accepted=} decision. A {@link Status#CONTRADICTION} surfaces as a strong advisory finding
 * (folded into the retry prompt and the reviewer surface); a hard REJECT is layered on top only behind a config flag (default off), matching the measure-before-hard-gate
 * discipline.
 * On any doubt the check fails OPEN ({@link Status#INCONCLUSIVE}) so it can never fabricate a false reject.
 *
 * @param status                     the cross-check verdict
 * @param contradictedTests          the shadow tests the REAL solution FAILS (build/compile gates excluded), parser form; the exact behaviours the solution contradicts
 * @param shadowTestNames            every shadow test name that ran against the solution, parser form
 * @param shadowTestsAgainstSolution the number of shadow tests that actually ran against the real solution
 * @param shadowTestsFailingTemplate how many shadow tests fail on the template (a discrimination hint only; NOT part of the reject decision)
 * @param detail                     a short human-readable explanation (why it was skipped/inconclusive, or a summary); may be {@code null}
 */
public record CorrectnessCrossCheck(Status status, List<String> contradictedTests, List<String> shadowTestNames, int shadowTestsAgainstSolution, int shadowTestsFailingTemplate,
        @Nullable String detail) {

    /** The cross-check verdict. */
    public enum Status {
        /** The solution passes every independently-authored contract test — no contradiction found. */
        CONSISTENT,
        /** The solution FAILS at least one independently-authored contract test — it contradicts its own stated contract (an unambiguous correctness defect). */
        CONTRADICTION,
        /** The shadow suite did not run against the solution (did not compile / no tests / timed out); fail-open, never a reject. */
        INCONCLUSIVE,
        /** The cross-check did not run (flag off, language not allowlisted, or no shadow suite was authored). */
        SKIPPED
    }

    public boolean isContradiction() {
        return status == Status.CONTRADICTION;
    }

    /** The cross-check did not run (flag off / language not allowlisted / no shadow suite). */
    public static CorrectnessCrossCheck skipped(String why) {
        return new CorrectnessCrossCheck(Status.SKIPPED, List.of(), List.of(), 0, 0, why);
    }

    /** The shadow suite did not compile/run against the real solution, so no conclusion can be drawn — never a reject (fail-open). */
    public static CorrectnessCrossCheck inconclusive(String why) {
        return new CorrectnessCrossCheck(Status.INCONCLUSIVE, List.of(), List.of(), 0, 0, why);
    }

    /**
     * Folds the contradiction into the verifier-feedback retry prompt while attempts remain, mirroring {@code SpecFidelityCriticService.renderForRetryPrompt}. Empty for any
     * non-contradiction status, so the caller can append it unconditionally.
     *
     * @return a retry-prompt fragment naming the contradicted behaviours, or an empty string when there is no contradiction
     */
    public String renderForRetryPrompt() {
        if (!isContradiction()) {
            return "";
        }
        return "\n\nAn INDEPENDENT examiner authored tests from your problem statement's stated contract alone (it never saw your reference solution) and your reference solution "
                + "FAILS these: " + String.join(", ", contradictedTests) + ". This means your solution contradicts its own stated behaviour — a real correctness bug (not a test "
                + "problem). Re-read the problem statement's contract and worked examples, then FIX the reference solution so it satisfies them. If instead the STATED contract is "
                + "wrong, correct the problem statement AND your own tests to match the behaviour you actually want.";
    }

    /**
     * The advisory surface: a {@link SpecFidelityReport.Finding} of kind {@link SpecFidelityReport.Kind#CONTRACT_CONTRADICTION}, so the contradiction flows through the existing
     * advisory plumbing (retry prompt while attempts remain, review comments on the final exercise) with no new wiring.
     *
     * @return the advisory finding describing the contradicted contract
     */
    public SpecFidelityReport.Finding toAdvisoryFinding() {
        return new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, String.join(", ", contradictedTests),
                "An independently-authored test derived only from your problem statement's stated contract FAILS on your reference solution — the solution contradicts its own stated "
                        + "behaviour. Fix the solution (or the statement, if the stated contract is wrong).");
    }
}
