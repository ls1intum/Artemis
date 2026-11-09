package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress.RepairRound;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;

/** Per-run state for semantic repair scheduling, budgets, and review-round accounting. */
class RepairRoundScheduler {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private static final int MAX_IDENTITY_REQUIREMENT_CHARS = 300;

    /** A specification rule label such as "R3" does not change a finding's identity. */
    private static final Pattern SPECIFICATION_RULE_LABEL = Pattern.compile("^[rs]\\d+\\s+");

    private final int roundLimit;

    private int roundsStarted;

    private boolean witnessAdoptionAttempted;

    private boolean reviewRetried;

    private final Set<RepairSurface> repairedSurfaces = EnumSet.noneOf(RepairSurface.class);

    private final Set<RepairSurface> unchangedSurfaces = EnumSet.noneOf(RepairSurface.class);

    @Nullable
    private RepairSurface currentSurface;

    private int consecutiveRoundsOnSurface;

    // Updated only for completed reviews, so a mechanical rejection cannot erase the comparison baseline.
    private Set<String> previousRoundFindingIdentities = Set.of();

    private int reviewRounds;

    RepairRoundScheduler(int roundLimit) {
        this.roundLimit = roundLimit;
    }

    int roundsStarted() {
        return roundsStarted;
    }

    int roundLimit() {
        return roundLimit;
    }

    boolean budgetExhausted() {
        return roundsStarted >= roundLimit;
    }

    Optional<SemanticRepairBatch> witnessAdoption(SpecFidelityReport report) {
        if (witnessAdoptionAttempted || budgetExhausted()) {
            return Optional.empty();
        }
        return SemanticRepairBatch.witnessAdoption(report);
    }

    void markWitnessAdoptionAttempted() {
        witnessAdoptionAttempted = true;
    }

    Optional<SemanticRepairBatch> nextRepairBatch(SpecFidelityReport report) {
        SpecFidelityReport remaining = new SpecFidelityReport(
                report.findings().stream().filter(finding -> !unchangedSurfaces.contains(SemanticRepairBatch.surfaceFor(finding.kind()))).toList());
        return SemanticRepairBatch.next(remaining, repairedSurfaces, currentSurface, consecutiveRoundsOnSurface);
    }

    /** An unchanged repair must not consume another surface's opportunity to address the same candidate. */
    boolean skipUnchangedSurface(SpecFidelityReport report) {
        if (currentSurface == null || budgetExhausted()) {
            return false;
        }
        unchangedSurfaces.add(currentSurface);
        return nextRepairBatch(report).isPresent();
    }

    void recordRepairRound(RepairSurface surface) {
        roundsStarted++;
        consecutiveRoundsOnSurface = surface == currentSurface ? consecutiveRoundsOnSurface + 1 : 1;
        currentSurface = surface;
        repairedSurfaces.add(surface);
    }

    /** Adoption spends budget but claims no repair-surface fairness credit. */
    void recordAdoptionRound() {
        roundsStarted++;
    }

    /** Grants one clean re-review after the reviewer returned no verdict. */
    boolean claimReviewRetry() {
        if (reviewRetried) {
            return false;
        }
        reviewRetried = true;
        return true;
    }

    RepairRound recordReviewRound(SpecFidelityReport report, int attempt) {
        unchangedSurfaces.clear();
        Set<String> currentIdentities = findingIdentities(report);
        int carriedOver = (int) currentIdentities.stream().filter(previousRoundFindingIdentities::contains).count();
        int drained = (int) previousRoundFindingIdentities.stream().filter(identity -> !currentIdentities.contains(identity)).count();
        int fresh = currentIdentities.size() - carriedOver;
        int blocking = (int) report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking).count();
        int advisory = report.findings().size() - blocking;
        previousRoundFindingIdentities = currentIdentities;
        reviewRounds++;
        return new RepairRound(reviewRounds, attempt, blocking, advisory, carriedOver, drained, fresh);
    }

    static String roundMessage(RepairRound round) {
        int total = round.carriedOver() + round.fresh();
        if (total == 0) {
            return "Quality review round " + round.round() + ": no issues remain.";
        }
        String issues = total + (total == 1 ? " issue" : " issues");
        if (round.round() == 1) {
            return "Quality review round " + round.round() + ": " + issues + " found.";
        }
        return "Quality review round " + round.round() + ": " + issues + " — " + round.carriedOver() + " still open from the previous round, " + round.drained() + " resolved, "
                + round.fresh() + " new.";
    }

    /**
     * Inconclusive mutant probes prove neither a gap nor its absence, so they never become findings; the round event still says how many there were, so a run whose executable
     * review mostly failed to execute cannot read as a clean one.
     */
    static String inconclusiveProbeNote(int inconclusiveMutantProbes) {
        if (inconclusiveMutantProbes == 0) {
            return "";
        }
        String probes = inconclusiveMutantProbes == 1 ? " mutant probe was" : " mutant probes were";
        return " " + inconclusiveMutantProbes + probes + " inconclusive and proved nothing either way.";
    }

    /** Distinguishes unavailable review evidence, convergence, and missing surface coverage. */
    static TerminationReason reasonForUnschedulableReport(SpecFidelityReport report) {
        if (hasInstrumentUnavailableFinding(report)) {
            return TerminationReason.REVIEW_UNAVAILABLE;
        }
        if (!report.hasBlockingFindings()) {
            return TerminationReason.CONVERGED;
        }
        // A contested concept outranks the other unschedulable blockers because it is upstream of them: the specification, the tests, and the statement all instantiate it, so
        // "no repair surface" would name the symptom while hiding the cause the instructor actually has to rule on.
        if (report.findings().stream().anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.CONCEPT_ADMISSION_FINDING)) {
            return TerminationReason.CONCEPT_ADMITTED_WITH_FINDINGS;
        }
        return TerminationReason.NO_SCHEDULABLE_SURFACE;
    }

    static boolean hasPrimaryReviewUnavailableFinding(SpecFidelityReport report) {
        return report.findings().stream().anyMatch(
                finding -> finding.kind() == SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE || finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    static boolean hasInstrumentUnavailableFinding(SpecFidelityReport report) {
        return hasPrimaryReviewUnavailableFinding(report)
                || report.findings().stream().anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_EVIDENCE_UNAVAILABLE);
    }

    /**
     * Identity uses kind plus normalized requirement, not free-form detail. Exact matching knowingly records a rephrased defect as one drained and one fresh rather than relying
     * on an uncalibrated similarity threshold.
     */
    private static Set<String> findingIdentities(SpecFidelityReport report) {
        return report.findings().stream().map(RepairRoundScheduler::findingIdentity).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String findingIdentity(SpecFidelityReport.Finding finding) {
        return finding.kind().name() + '\n' + normalizeRequirement(finding.requirement());
    }

    /**
     * Whether a semantic repair made progress. Executed evidence decides first; a repair whose suite lets a previously killed mutant survive again never reaches this
     * comparison, because verification rejects it. A repair that fixed an executed reference failure or killed a previously surviving mutant improved, even if a freshly authored
     * mutant now survives: that survivor is
     * new evidence, not a loss, and becomes the next oracle
     * target within the round budget, while text-only blockers raised beside the kill stay on the repaired candidate, because a re-review re-rolls text verdicts. Fewer
     * executed blockers is progress too; a prior survivor without a fresh verdict must be in {@code current} so that losing its evidence never reads as draining it. Otherwise the
     * reviewed blockers must strictly shrink, because text identity is then the only evidence there is. A blocker no repair
     * surface can address and that is unchanged on both sides (a contested specification or concept, for example) is ignored there: no repair could have drained it, so it
     * must not turn a round that drained nothing else, such as a witness adoption, into a regression.
     *
     * @param repairedExecutedDefect whether execution proved a reference fix or a kill of a mutant the previous candidate's suite let survive
     */
    static boolean repairImproved(SpecFidelityReport previous, SpecFidelityReport current, boolean repairedExecutedDefect) {
        if (repairedExecutedDefect || candidateBlockingFindingIdentities(current, true).size() < candidateBlockingFindingIdentities(previous, true).size()) {
            return true;
        }
        Set<String> previousBlockers = candidateBlockingFindingIdentities(previous, false);
        Set<String> currentBlockers = candidateBlockingFindingIdentities(current, false);
        Set<String> persistentUnrepairable = previous.findings().stream().filter(SpecFidelityReport.Finding::isBlocking)
                .filter(finding -> SemanticRepairBatch.surfaceFor(finding.kind()) == null).map(RepairRoundScheduler::findingIdentity).filter(currentBlockers::contains)
                .collect(Collectors.toSet());
        previousBlockers.removeAll(persistentUnrepairable);
        currentBlockers.removeAll(persistentUnrepairable);
        if (previousBlockers.isEmpty()) {
            return currentBlockers.isEmpty();
        }
        return currentBlockers.size() < previousBlockers.size() && previousBlockers.containsAll(currentBlockers);
    }

    private static Set<String> candidateBlockingFindingIdentities(SpecFidelityReport report, boolean executedOnly) {
        return report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking).filter(finding -> switch (finding.kind()) {
            case QUALITY_REVIEW_UNAVAILABLE, ADAPTATION_SCOPE_REVIEW_UNAVAILABLE, EXECUTABLE_EVIDENCE_UNAVAILABLE, CONTRACT_WITNESS_ADJUDICATION_UNAVAILABLE -> false;
            case EXECUTABLE_WEAK_TEST_ORACLE -> true;
            default -> !executedOnly;
        }).map(RepairRoundScheduler::findingIdentity).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalizeRequirement(String requirement) {
        String normalized = NON_ALPHANUMERIC.matcher(requirement.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
        normalized = SPECIFICATION_RULE_LABEL.matcher(normalized).replaceFirst("");
        return normalized.length() <= MAX_IDENTITY_REQUIREMENT_CHARS ? normalized : normalized.substring(0, MAX_IDENTITY_REQUIREMENT_CHARS);
    }
}
