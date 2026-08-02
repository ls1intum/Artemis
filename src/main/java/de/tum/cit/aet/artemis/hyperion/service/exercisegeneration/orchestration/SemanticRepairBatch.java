package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;

/**
 * One semantic repair round: the surface it may touch, the findings it must address, and the workspace roots it may write. Which round comes next is decided here rather than in
 * {@link GenerationAttemptLoop} because the scheduling rule is a pure function of the findings and the fairness state, and is tested as one.
 */
record SemanticRepairBatch(RepairSurface surface, SpecFidelityReport report, Set<String> writableRoots) {

    /**
     * How many rounds in a row one surface may hold before a surface that has never been repaired takes precedence. Greater than one because strengthening a single surface can
     * legitimately take several rounds, each closing a different gap; bounded so a surface still waiting for its first round cannot be starved indefinitely.
     */
    private static final int MAX_CONSECUTIVE_ROUNDS_PER_SURFACE = 2;

    /**
     * The oracle batch that offers the agent every validated contract witness. Separate from {@link #next} because that one deliberately schedules only blocking findings,
     * and a witness is advisory: it is a test the agent may adopt, not a defect it must fix.
     */
    static Optional<SemanticRepairBatch> witnessAdoption(SpecFidelityReport report) {
        List<SpecFidelityReport.Finding> witnesses = report.findings().stream().filter(finding -> finding.kind() == SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE).toList();
        return witnesses.isEmpty() ? Optional.empty()
                : Optional.of(new SemanticRepairBatch(RepairSurface.ORACLE, new SpecFidelityReport(witnesses), writableRootsFor(RepairSurface.ORACLE)));
    }

    /**
     * The next repair batch. Exactly one coherent surface per attempt, so a single repair cannot rewrite every artifact at once, and chosen by priority only while no surface is
     * being starved: a surface that has held {@link #MAX_CONSECUTIVE_ROUNDS_PER_SURFACE} rounds in a row yields to any surface with blocking findings that has never had a round.
     * Priority alone would let one surface hold the entire budget while another shipped unrepaired.
     *
     * @param report            the current review findings
     * @param repairedSurfaces  surfaces already repaired at least once in this generation
     * @param currentSurface    the surface the previous round repaired, or {@code null} for the first round
     * @param consecutiveRounds how many rounds in a row {@code currentSurface} has held
     */
    static Optional<SemanticRepairBatch> next(SpecFidelityReport report, Set<RepairSurface> repairedSurfaces, @Nullable RepairSurface currentSurface, int consecutiveRounds) {
        if (currentSurface != null && consecutiveRounds >= MAX_CONSECUTIVE_ROUNDS_PER_SURFACE) {
            Optional<SemanticRepairBatch> neverRepaired = batchFor(report, surface -> !repairedSurfaces.contains(surface));
            if (neverRepaired.isPresent()) {
                return neverRepaired;
            }
        }
        return batchFor(report, surface -> true);
    }

    private static Optional<SemanticRepairBatch> batchFor(SpecFidelityReport report, Predicate<RepairSurface> eligible) {
        for (RepairSurface surface : RepairSurface.values()) {
            if (!eligible.test(surface)) {
                continue;
            }
            List<SpecFidelityReport.Finding> findings = report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking)
                    .filter(finding -> surfaceFor(finding.kind()) == surface).toList();
            if (!findings.isEmpty()) {
                if (surface == RepairSurface.ORACLE) {
                    findings = java.util.stream.Stream
                            .concat(findings.stream(), report.findings().stream().filter(finding -> finding.kind() == SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE)).toList();
                }
                return Optional.of(new SemanticRepairBatch(surface, new SpecFidelityReport(findings), writableRootsFor(surface)));
            }
        }
        return Optional.empty();
    }

    static @Nullable RepairSurface surfaceFor(SpecFidelityReport.Kind kind) {
        return switch (kind) {
            // A validated witness is a test to adopt, so it belongs to the oracle surface. Being advisory it never triggers a repair by itself (the loop stops when nothing
            // blocks); it rides along with an oracle repair that is already happening, and otherwise reaches the instructor as a review comment.
            case UNCOVERED_REQUIREMENT, WEAK_TEST_ORACLE, EXECUTABLE_WEAK_TEST_ORACLE, EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL, CONTRACT_WITNESS_AVAILABLE -> RepairSurface.ORACLE;
            case TEMPLATE_QUALITY_GAP -> RepairSurface.SCAFFOLD;
            case MECHANICS_LEAK, INVENTED_REQUIREMENT, UNREQUESTED_ADAPTATION_CHANGE, REQUESTED_ADAPTATION_CHANGE_MISSING, CONTRACT_CONTRADICTION, HIDDEN_GRADED_REQUIREMENT ->
                RepairSurface.CONTRACT;
            // A technique mandate blocks autonomous publication but has no repair surface: no assertion distinguishes a recursive implementation from an iterative one with the
            // same results. Leaving it unscheduled saves the verified candidate for an instructor decision instead of burning rounds on source inspection or proxy metrics.
            case MISSING_WORKED_EXAMPLE, MISSING_FAILURE_MESSAGE, ADAPTATION_SCOPE_REVIEW_UNAVAILABLE, EXECUTABLE_EVIDENCE_UNAVAILABLE, QUALITY_REVIEW_UNAVAILABLE,
                    UNENFORCEABLE_TECHNIQUE_RULE, SPECIFICATION_REVIEW_FINDING, CONTRACT_WITNESS_ADJUDICATION_UNAVAILABLE ->
                null;
        };
    }

    private static Set<String> writableRootsFor(RepairSurface surface) {
        return switch (surface) {
            case ORACLE -> Set.of("tests", "test-plan.json", "problem-statement.md");
            case SCAFFOLD -> Set.of("solution", "template", "problem-statement.md");
            case CONTRACT -> Set.of("solution", "template", "tests", "test-plan.json", "problem-statement.md");
        };
    }

    String guidance() {
        return switch (surface) {
            case ORACLE ->
                "Keep the already verified solution unchanged while strengthening the test unless a sound new assertion proves that solution violates the frozen contract. "
                        + "For collaboration or delegation, use a test-controlled fake or recording collaborator that returns a unique sentinel and records its input; assert forwarding "
                        + "and return propagation against that witness. Do not call a production collaborator twice to manufacture an identity comparison. Do not add production caching, "
                        + "memoization, shared/global state, or repeated-call identity semantics solely to make a new oracle test pass unless the reviewed finding and frozen contract "
                        + "explicitly require that behavior. ";
            case SCAFFOLD ->
                "Repair only the starter/reference scaffold and its point-of-use student guidance. A type marked given is supplied code, so keep its canonical source identical in "
                        + "solution and template; never weaken supplied code merely to force the starter to fail a test. ";
            case CONTRACT ->
                "Reconcile only the contradictory or invented contract surface named in the evidence. Preserve the frozen specification and every unaffected behavior, test, API, and "
                        + "example. ";
        };
    }
}
