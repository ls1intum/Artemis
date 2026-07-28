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

/**
 * The repair phase of one generation run, as a state machine over the review reports it is shown: which scoped repair round comes next, whether one is still affordable, and how
 * this round's findings relate to the previous round's.
 * <p>
 * A plain per-run object rather than a Spring bean, for the same reason {@link GenerationAttemptLoop} is one: every field is loop-carried state of a single run. It is split out
 * of that loop because these members answer one question and nothing else — none of them reads the sandbox, the verifier, the workspace, the exercise, or a model. The loop owns
 * the sandbox lifecycle, the mechanical budget, and the prompts; it consults this object for the repair phase's decisions and records the answers.
 * <p>
 * Three separate once-per-run budgets live here because all three are spent by the same phase and each is meaningless without the others: the semantic repair rounds themselves
 * ({@link #recordRepairRound}), the single witness-adoption round ({@link #markWitnessAdoptionAttempted}), and the single re-review after the reviewer failed to return a verdict
 * ({@link #claimReviewRetry}). Splitting them would let a caller spend one while believing another was still available.
 * <p>
 * The surface fairness rule itself is {@link SemanticRepairBatch#next}, a pure function; what lives here is the state that function is asked about.
 */
class RepairRoundScheduler {

    /** Bounds one finding-identity key. A reviewer that emits a whole paragraph as a "requirement" must not turn the per-round identity set into an unbounded allocation. */
    private static final int MAX_IDENTITY_REQUIREMENT_CHARS = 300;

    /**
     * The specification's own rule label ("R3"), which the reviewer cites for the same defect only about half the time — "R3 reverse must handle the empty string" and "reverse
     * must handle the empty string" name one defect. Stripping the citation merges them; measured over 30 reviews of one unchanged candidate it removed three duplicate
     * identities and raised apparent stability without merging any two distinct defects, because the label carries no information the requirement text does not.
     */
    private static final Pattern SPECIFICATION_RULE_LABEL = Pattern.compile("^[rs]\\d+\\s+");

    /** How many semantic repair rounds this run may start at all. */
    private final int roundLimit;

    private int roundsStarted;

    // At most one witness-adoption round per generation, so offering ready-to-adopt tests can never turn into repeated rewrites of a finished candidate.
    private boolean witnessAdoptionAttempted;

    private boolean reviewRetried;

    // Fairness state for the surface scheduler; see SemanticRepairBatch#next.
    private final Set<RepairSurface> repairedSurfaces = EnumSet.noneOf(RepairSurface.class);

    @Nullable
    private RepairSurface currentSurface;

    private int consecutiveRoundsOnSurface;

    // The identities of the previous review round's findings, so drain can be measured per finding rather than per category. Survives a mechanically rejected attempt (which
    // resets the report to empty without reviewing anything), so the next real review still compares against the last real one.
    private Set<String> previousRoundFindingIdentities = Set.of();

    private int reviewRounds;

    /**
     * @param roundLimit how many semantic repair rounds this run may start
     */
    RepairRoundScheduler(int roundLimit) {
        this.roundLimit = roundLimit;
    }

    /** @return how many semantic repair rounds have been started, adoption rounds included */
    int roundsStarted() {
        return roundsStarted;
    }

    /** @return how many semantic repair rounds this run may start in total */
    int roundLimit() {
        return roundLimit;
    }

    /** @return whether the semantic repair budget is spent, so no further round may be scheduled */
    boolean budgetExhausted() {
        return roundsStarted >= roundLimit;
    }

    /**
     * The witness-adoption batch still available to this run, if any.
     * <p>
     * Guarded by both once-per-run conditions the loop must not have to remember: the run's single adoption round, and the shared repair budget an adoption round also spends.
     *
     * @param report the current review findings
     * @return the batch offering every validated contract witness, or empty when none is offered or the run may not take one
     */
    Optional<SemanticRepairBatch> witnessAdoption(SpecFidelityReport report) {
        if (witnessAdoptionAttempted || budgetExhausted()) {
            return Optional.empty();
        }
        return SemanticRepairBatch.witnessAdoption(report);
    }

    /** Spends the run's single witness-adoption opportunity, whether or not the agent acts on the offer. */
    void markWitnessAdoptionAttempted() {
        witnessAdoptionAttempted = true;
    }

    /**
     * The next scoped repair batch for {@code report} under this run's fairness state.
     *
     * @param report the current review findings
     * @return the batch to repair, or empty when no blocking finding maps to a repairable surface
     */
    Optional<SemanticRepairBatch> nextRepairBatch(SpecFidelityReport report) {
        return SemanticRepairBatch.next(report, repairedSurfaces, currentSurface, consecutiveRoundsOnSurface);
    }

    /**
     * Records a repair round on {@code surface}: it spends a round of the budget and claims the surface's fairness credit.
     *
     * @param surface the surface this round repairs
     */
    void recordRepairRound(RepairSurface surface) {
        roundsStarted++;
        consecutiveRoundsOnSurface = surface == currentSurface ? consecutiveRoundsOnSurface + 1 : 1;
        currentSurface = surface;
        repairedSurfaces.add(surface);
    }

    /**
     * Records the witness-adoption round: it spends a round of the budget but deliberately claims no fairness credit.
     * <p>
     * Fairness bookkeeping is over surfaces that were actually REPAIRED. An adoption round only offers optional tests, so recording it as a repair would make the scheduler
     * believe the oracle surface already had its turn and deny it the unrepaired-surface preference when a genuine weak oracle appears.
     */
    void recordAdoptionRound() {
        roundsStarted++;
    }

    /**
     * Takes the run's single re-review opportunity, used when the reviewer returned no verdict at all.
     * <p>
     * "The review could not complete" is not a statement about the exercise. Failing open on the VERDICT is right — a broken reviewer must never reject a mechanically sound
     * candidate — but failing open on the WORK is not: ending with repair rounds unspent because the reviewer had a bad turn reports a quality gap the reviewer never found.
     *
     * @return {@code true} the first time it is called in a run, {@code false} every time after
     */
    boolean claimReviewRetry() {
        if (reviewRetried) {
            return false;
        }
        reviewRetried = true;
        return true;
    }

    /**
     * Records how this review's findings relate to the previous review's.
     * <p>
     * Only a completed review is a round: a mechanically rejected attempt resets the report to empty without asking the reviewer anything, and counting that as "every finding
     * drained" would report the repair loop working precisely when it is not — so this is called from the review path only. Purely observational; no scheduling decision here or
     * in the loop reads the counts.
     *
     * @param report  the review just completed
     * @param attempt the authoring attempt whose candidate was reviewed
     * @return this round's bookkeeping, for the caller to log and emit
     */
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

    /**
     * The instructor-facing line for one review round.
     *
     * @param round the round's bookkeeping
     * @return the progress message to emit alongside it
     */
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

    /**
     * Why a run ends when the scheduler has no batch to offer. The three cases look identical from the outside and call for opposite fixes, so they are never collapsed:
     * a reviewer that could not produce a verdict is an instrument failure, a candidate whose remaining findings are all advisory is a converged run, and a blocking finding that
     * maps to no repair surface is a gap in the surface map.
     *
     * @param report the review the scheduler was given
     * @return the reason to record for this exit
     */
    static TerminationReason reasonForUnschedulableReport(SpecFidelityReport report) {
        if (hasReviewUnavailableFinding(report)) {
            return TerminationReason.REVIEW_UNAVAILABLE;
        }
        if (!report.hasBlockingFindings()) {
            return TerminationReason.CONVERGED;
        }
        return TerminationReason.NO_SCHEDULABLE_SURFACE;
    }

    /**
     * Whether the report says "the review could not complete" rather than anything about the exercise.
     *
     * @param report the review to classify
     * @return {@code true} when the reviewer failed to return a verdict
     */
    static boolean hasReviewUnavailableFinding(SpecFidelityReport report) {
        return report.findings().stream().anyMatch(
                finding -> finding.kind() == SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE || finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    /**
     * The identity of every finding in {@code report}, deduplicated.
     * <p>
     * Identity is the finding's {@link SpecFidelityReport.Kind} plus its normalised {@code requirement}, and deliberately not the whole record: {@code detail} is prose the
     * reviewer rewrites freely between rounds while the underlying defect does not move, so hashing it would report every finding as fresh and make drain unmeasurable. The
     * requirement is the defect's own name ("CJK graphemes are not counted", "throws on zero capacity"), and the kind is kept because the same requirement under a different kind
     * is a different defect calling for a different repair.
     * <p>
     * Matching is exact on the normalised text, with no similarity threshold: a threshold cannot be calibrated until the reviewer's own run-to-run stability is measured, and an
     * uncalibrated one would silently decide the very question this instrument exists to answer. The known cost is stated rather than hidden — a reviewer that rephrases the same
     * defect registers it as one drained plus one fresh, which overstates drain. Normalisation removes the cheap half of that (casing, punctuation, quoting, whitespace).
     */
    private static Set<String> findingIdentities(SpecFidelityReport report) {
        return report.findings().stream().map(RepairRoundScheduler::findingIdentity).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String findingIdentity(SpecFidelityReport.Finding finding) {
        return finding.kind().name() + '\n' + normalizeRequirement(finding.requirement());
    }

    private static String normalizeRequirement(String requirement) {
        String normalized = requirement.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
        normalized = SPECIFICATION_RULE_LABEL.matcher(normalized).replaceFirst("");
        return normalized.length() <= MAX_IDENTITY_REQUIREMENT_CHARS ? normalized : normalized.substring(0, MAX_IDENTITY_REQUIREMENT_CHARS);
    }
}
