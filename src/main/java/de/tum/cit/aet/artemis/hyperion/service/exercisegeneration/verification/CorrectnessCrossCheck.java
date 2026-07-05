package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;

/**
 * Result of the DECORRELATED correctness cross-check: an INDEPENDENTLY-authored test suite (written by a separate examiner agent from the problem statement's stated contract,
 * never
 * from the reference solution) run against the REAL solution through the SAME production build+parse path the differential oracle uses.
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
 * @param status            the cross-check verdict
 * @param contradictedTests the shadow tests the REAL solution FAILS (build/compile gates excluded), parser form; the exact behaviours the solution contradicts
 * @param detail            a short human-readable explanation (why it was skipped/inconclusive, or a summary); may be {@code null}
 */
public record CorrectnessCrossCheck(Status status, List<String> contradictedTests, @Nullable String detail) {

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

    /** The cross-check did not run; see {@link Status#SKIPPED}. */
    public static CorrectnessCrossCheck skipped(String why) {
        return new CorrectnessCrossCheck(Status.SKIPPED, List.of(), why);
    }

    /** Fail-open: no conclusion could be drawn, so this is never a reject. See {@link Status#INCONCLUSIVE}. */
    public static CorrectnessCrossCheck inconclusive(String why) {
        return new CorrectnessCrossCheck(Status.INCONCLUSIVE, List.of(), why);
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
