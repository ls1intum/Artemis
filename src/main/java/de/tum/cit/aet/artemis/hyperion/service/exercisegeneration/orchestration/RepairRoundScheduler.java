package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;

/** Per-run state for semantic repair scheduling, budgets, and review-round accounting. */
class RepairRoundScheduler {

    private static final int MAX_IDENTITY_REQUIREMENT_CHARS = 300;

    /** A specification rule label such as "R3" does not change a finding's identity. */
    private static final Pattern SPECIFICATION_RULE_LABEL = Pattern.compile("^[rs]\\d+\\s+");

    private final int roundLimit;

    private int roundsStarted;

    private boolean witnessAdoptionAttempted;

    private boolean reviewRetried;

    private final Set<RepairSurface> repairedSurfaces = EnumSet.noneOf(RepairSurface.class);

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
        return SemanticRepairBatch.next(report, repairedSurfaces, currentSurface, consecutiveRoundsOnSurface);
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

    ExerciseGenerationRepairRoundDTO recordReviewRound(SpecFidelityReport report, int attempt) {
        Set<String> currentIdentities = findingIdentities(report);
        int carriedOver = (int) currentIdentities.stream().filter(previousRoundFindingIdentities::contains).count();
        int drained = (int) previousRoundFindingIdentities.stream().filter(identity -> !currentIdentities.contains(identity)).count();
        int fresh = currentIdentities.size() - carriedOver;
        int blocking = (int) report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking).count();
        int advisory = report.findings().size() - blocking;
        previousRoundFindingIdentities = currentIdentities;
        reviewRounds++;
        return new ExerciseGenerationRepairRoundDTO(reviewRounds, attempt, blocking, advisory, carriedOver, drained, fresh);
    }

    static String roundMessage(ExerciseGenerationRepairRoundDTO round) {
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

    /** Distinguishes unavailable review evidence, convergence, and missing surface coverage. */
    static TerminationReason reasonForUnschedulableReport(SpecFidelityReport report) {
        if (hasInstrumentUnavailableFinding(report)) {
            return TerminationReason.REVIEW_UNAVAILABLE;
        }
        if (!report.hasBlockingFindings()) {
            return TerminationReason.CONVERGED;
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

    static boolean repairImproved(SpecFidelityReport previous, SpecFidelityReport current) {
        Set<String> previousBlockers = candidateBlockingFindingIdentities(previous);
        Set<String> currentBlockers = candidateBlockingFindingIdentities(current);
        if (previousBlockers.isEmpty()) {
            return currentBlockers.isEmpty();
        }
        return currentBlockers.size() < previousBlockers.size() && previousBlockers.containsAll(currentBlockers);
    }

    private static Set<String> candidateBlockingFindingIdentities(SpecFidelityReport report) {
        return report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking).filter(finding -> switch (finding.kind()) {
            case QUALITY_REVIEW_UNAVAILABLE, ADAPTATION_SCOPE_REVIEW_UNAVAILABLE, EXECUTABLE_EVIDENCE_UNAVAILABLE, CONTRACT_WITNESS_ADJUDICATION_UNAVAILABLE -> false;
            default -> true;
        }).map(RepairRoundScheduler::findingIdentity).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalizeRequirement(String requirement) {
        String normalized = requirement.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
        normalized = SPECIFICATION_RULE_LABEL.matcher(normalized).replaceFirst("");
        return normalized.length() <= MAX_IDENTITY_REQUIREMENT_CHARS ? normalized : normalized.substring(0, MAX_IDENTITY_REQUIREMENT_CHARS);
    }
}
