package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;

/**
 * Result of the cross-check: a test suite authored independently by a separate examiner agent from the problem statement's stated contract (not from the reference solution), run
 * against the solution through the same production build+parse path the differential oracle uses.
 * <p>
 * The differential oracle ({@link DifferentialVerificationService}) relates four artifacts the same agent authored (solution/template/tests/problem-statement), so a wrong model
 * encoded consistently across the solution and its co-authored tests is invisible to it. This report breaks that correlation: a solution that fails a test derived only from its
 * own
 * stated contract contradicts its own statement — a correctness defect.
 * <p>
 * Additive and advisory by default: it never loosens the oracle's {@code accepted=} decision. A {@link Status#CONTRADICTION} surfaces as an advisory finding (folded into the retry
 * prompt and the reviewer surface); a hard reject is layered on top only behind a config flag (default off). On any doubt the check fails open ({@link Status#INCONCLUSIVE}) so it
 * can never fabricate a false reject.
 *
 * @param status            the cross-check verdict
 * @param contradictedTests the shadow tests the solution fails (build/compile gates excluded), parser form; the behaviours the solution contradicts
 * @param detail            a short human-readable explanation (why it was skipped/inconclusive, or a summary); may be {@code null}
 */
public record CrossCheckVerdict(Status status, List<String> contradictedTests, @Nullable String detail) {

    /** The cross-check verdict. */
    public enum Status {
        /** The solution passes every independently-authored contract test — no contradiction found. */
        CONSISTENT,
        /** The solution fails at least one independently-authored contract test — it contradicts its own stated contract (a correctness defect). */
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
    public static CrossCheckVerdict skipped(String why) {
        return new CrossCheckVerdict(Status.SKIPPED, List.of(), why);
    }

    /** Fail-open: no conclusion could be drawn, so this is never a reject. See {@link Status#INCONCLUSIVE}. */
    public static CrossCheckVerdict inconclusive(String why) {
        return new CrossCheckVerdict(Status.INCONCLUSIVE, List.of(), why);
    }

    /**
     * The advisory surface: a {@link SpecFidelityReport.Finding} of kind {@link SpecFidelityReport.Kind#CONTRACT_CONTRADICTION}, so the contradiction flows through the advisory
     * plumbing (retry prompt while attempts remain, review comments on the final exercise).
     *
     * @return the advisory finding describing the contradicted contract
     */
    public SpecFidelityReport.Finding toAdvisoryFinding() {
        return new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, String.join(", ", contradictedTests),
                "An independently-authored test derived only from your problem statement's stated contract FAILS on your reference solution — the solution contradicts its own stated "
                        + "behaviour. Fix the solution (or the statement, if the stated contract is wrong).");
    }
}
