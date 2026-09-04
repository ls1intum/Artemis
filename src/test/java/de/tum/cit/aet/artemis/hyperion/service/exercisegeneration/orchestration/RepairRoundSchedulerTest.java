package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;

/**
 * The repair phase's state machine, driven directly: given a sequence of review reports, which surfaces does the scheduler pick, which budgets does it spend, and does a
 * rephrased finding register as drained-plus-fresh?
 * <p>
 * {@code SemanticRepairBatchTest} pins the scheduling rule as a pure function of hand-supplied state and {@code GenerationAttemptLoopTest} pins that the loop honours the
 * answer; covered here is the state accumulation in between, across a run.
 */
class RepairRoundSchedulerTest {

    /** Wider than any scenario needs, so a budget never silently ends a run that is testing something else. */
    private static final int UNBOUNDED_BUDGET = 99;

    private static SpecFidelityReport report(SpecFidelityReport.Kind... kinds) {
        return new SpecFidelityReport(Arrays.stream(kinds).map(kind -> finding(kind, "requirement for " + kind.name())).toList());
    }

    private static SpecFidelityReport.Finding finding(SpecFidelityReport.Kind kind, String requirement) {
        return new SpecFidelityReport.Finding(kind, requirement, "detail for " + requirement);
    }

    /** Blocking findings on the oracle and the scaffold at once, which is what makes the scheduler's choice observable at all. */
    private static SpecFidelityReport oracleAndScaffoldBlockers() {
        return report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP);
    }

    /** Asks the scheduler for a batch once per review and records each answer as a repair round, as the attempt loop does, stopping at the first unschedulable round. */
    private static List<RepairSurface> scheduleRounds(RepairRoundScheduler scheduler, List<SpecFidelityReport> reviews) {
        List<RepairSurface> scheduled = new ArrayList<>();
        for (SpecFidelityReport review : reviews) {
            Optional<SemanticRepairBatch> batch = scheduler.nextRepairBatch(review);
            if (batch.isEmpty()) {
                break;
            }
            scheduler.recordRepairRound(batch.get().surface());
            scheduled.add(batch.get().surface());
        }
        return scheduled;
    }

    @Nested
    class SurfaceSelection {

        static Stream<Arguments> surfaceSelection() {
            SpecFidelityReport oracleAndScaffold = oracleAndScaffoldBlockers();
            return Stream.of(
                    // One blocker, one surface: the finding's kind alone decides, and each of the three surfaces is reachable.
                    Arguments.of("a contract blocker alone", List.of(report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION)), List.of(RepairSurface.CONTRACT)),
                    Arguments.of("an oracle blocker alone", List.of(report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE)), List.of(RepairSurface.ORACLE)),
                    Arguments.of("a scaffold blocker alone", List.of(report(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP)), List.of(RepairSurface.SCAFFOLD)),
                    // An uncovered requirement is an oracle gap, not a contract one: the behaviour is agreed, only untested.
                    Arguments.of("an uncovered requirement", List.of(report(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT)), List.of(RepairSurface.ORACLE)),
                    // Declaration order in RepairSurface is the priority order, so a contract blocker outranks both others on the first round.
                    Arguments.of("contract, oracle and scaffold blockers together",
                            List.of(report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE,
                                    SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP)),
                            List.of(RepairSurface.CONTRACT)),
                    // Both surfaces block on every round, so priority alone would give ORACLE the whole run; only the accumulated consecutive-round count yields the third.
                    Arguments.of("a surface that has held two rounds yields to one never repaired", Collections.nCopies(4, oracleAndScaffold),
                            List.of(RepairSurface.ORACLE, RepairSurface.ORACLE, RepairSurface.SCAFFOLD, RepairSurface.ORACLE)),
                    // Yielding is only meaningful when something is waiting: with nothing else outstanding the leading surface continues rather than stalling the budget.
                    Arguments.of("a lone blocking surface keeps working past the consecutive cap",
                            Collections.nCopies(4, report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE)), Collections.nCopies(4, RepairSurface.ORACLE)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("surfaceSelection")
        void theScheduledSurfacesFollowTheFindingsAndTheFairnessState(String scenario, List<SpecFidelityReport> reviews, List<RepairSurface> expectedSurfaces) {
            RepairRoundScheduler scheduler = new RepairRoundScheduler(UNBOUNDED_BUDGET);

            assertThat(scheduleRounds(scheduler, reviews)).as(scenario).isEqualTo(expectedSurfaces);
        }
    }

    @Nested
    class Budgets {

        @Test
        void theBudgetIsExhaustedExactlyWhenTheLastGrantedRoundHasBeenRecorded() {
            RepairRoundScheduler scheduler = new RepairRoundScheduler(2);

            assertThat(scheduler.budgetExhausted()).as("no round started yet").isFalse();
            scheduler.recordRepairRound(RepairSurface.ORACLE);
            assertThat(scheduler.budgetExhausted()).as("one of two rounds spent").isFalse();
            scheduler.recordRepairRound(RepairSurface.ORACLE);
            assertThat(scheduler.budgetExhausted()).as("both rounds spent").isTrue();
            assertThat(scheduler.roundsStarted()).isEqualTo(2);
            assertThat(scheduler.roundLimit()).isEqualTo(2);
        }

        @Test
        void aZeroBudgetIsExhaustedBeforeAnyRoundIsScheduled() {
            // The ADAPT clamp and a misconfigured generation budget both reach this state; a run that starts a round anyway would exceed a limit its operator set.
            assertThat(new RepairRoundScheduler(0).budgetExhausted()).isTrue();
        }

        @Test
        void anAdoptionRoundSpendsARoundOfTheSharedBudget() {
            // Adoption is free of fairness credit but not of budget: two adoption rounds would be two rewrites of a finished exercise.
            RepairRoundScheduler scheduler = new RepairRoundScheduler(1);

            scheduler.recordAdoptionRound();

            assertThat(scheduler.roundsStarted()).isEqualTo(1);
            assertThat(scheduler.budgetExhausted()).isTrue();
        }
    }

    @Nested
    class WitnessAdoption {

        private static SpecFidelityReport witnessOffer() {
            return report(SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE);
        }

        @Test
        void aValidatedWitnessIsOfferedAsAnOracleBatchEvenThoughItBlocksNothing() {
            // The ordinary scheduler sees only blocking findings, so without this separate path a witness would never be offered at all.
            RepairRoundScheduler scheduler = new RepairRoundScheduler(UNBOUNDED_BUDGET);

            assertThat(scheduler.nextRepairBatch(witnessOffer())).as("advisory, so no ordinary repair round is due").isEmpty();
            assertThat(scheduler.witnessAdoption(witnessOffer()).orElseThrow().surface()).isEqualTo(RepairSurface.ORACLE);
        }

        @Test
        void theAdoptionOfferIsWithdrawnOnceTheRunHasTakenIt() {
            // One adoption round per run: offering ready-to-adopt tests must never turn into repeated rewrites of a candidate that already passed every gate.
            RepairRoundScheduler scheduler = new RepairRoundScheduler(UNBOUNDED_BUDGET);

            scheduler.markWitnessAdoptionAttempted();

            assertThat(scheduler.witnessAdoption(witnessOffer())).isEmpty();
        }

        @Test
        void anExhaustedRepairBudgetWithdrawsTheAdoptionOffer() {
            // Adoption spends the same budget as a repair, so offering one past the limit would grant a round the operator's budget does not cover.
            RepairRoundScheduler scheduler = new RepairRoundScheduler(1);
            scheduler.recordRepairRound(RepairSurface.CONTRACT);

            assertThat(scheduler.witnessAdoption(witnessOffer())).isEmpty();
        }

        @Test
        void anAdoptionRoundClaimsNoSurfaceFairnessCredit() {
            // Recording an adoption round as an oracle repair would make a genuine weak oracle appearing next yield to the scaffold a round early.
            RepairRoundScheduler scheduler = new RepairRoundScheduler(UNBOUNDED_BUDGET);
            scheduler.witnessAdoption(witnessOffer()).orElseThrow();
            scheduler.markWitnessAdoptionAttempted();
            scheduler.recordAdoptionRound();

            List<RepairSurface> scheduled = scheduleRounds(scheduler, Collections.nCopies(3, oracleAndScaffoldBlockers()));

            assertThat(scheduled).containsExactly(RepairSurface.ORACLE, RepairSurface.ORACLE, RepairSurface.SCAFFOLD);
        }
    }

    @Nested
    class ReviewRetry {

        @Test
        void theReReviewIsClaimableExactlyOnce() {
            // Ending with repair rounds unspent because the reviewer had a bad turn reports a quality gap the reviewer never found; one retry, not a budget's worth.
            RepairRoundScheduler scheduler = new RepairRoundScheduler(UNBOUNDED_BUDGET);

            assertThat(scheduler.claimReviewRetry()).as("first claim").isTrue();
            assertThat(scheduler.claimReviewRetry()).as("second claim").isFalse();
            assertThat(scheduler.claimReviewRetry()).as("third claim").isFalse();
        }

        @Test
        void claimingTheReReviewSpendsNoRepairRound() {
            // The retry buys a verdict, not a repair. Charging it to the repair budget would take a round away from the findings that verdict is about to reveal.
            RepairRoundScheduler scheduler = new RepairRoundScheduler(1);

            scheduler.claimReviewRetry();

            assertThat(scheduler.roundsStarted()).isZero();
            assertThat(scheduler.budgetExhausted()).isFalse();
        }
    }

    @Nested
    class UnschedulableReportClassification {

        @Test
        void aBlockingFindingWithNoRepairSurfaceIsReportedAsNoSchedulableSurface() {
            // Pinned directly on the classifier: no blocking Kind currently maps to a null surface, so the loop cannot reach this state through the engine.
            SpecFidelityReport blockingWithNoSurface = new SpecFidelityReport(
                    List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "unschedulable", "no surface owns it")));

            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(blockingWithNoSurface)).isEqualTo(TerminationReason.NO_SCHEDULABLE_SURFACE);
        }

        @Test
        void anUnschedulableReportWithoutBlockersIsReportedAsConverged() {
            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(report(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE))).isEqualTo(TerminationReason.CONVERGED);
            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(SpecFidelityReport.empty())).isEqualTo(TerminationReason.CONVERGED);
        }

        @Test
        void anUnschedulableReportFromAFailedReviewIsReportedAsReviewUnavailable() {
            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(SpecFidelityReport.qualityReviewUnavailable("no verdict")))
                    .isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(SpecFidelityReport.adaptationScopeUnavailable("no verdict")))
                    .isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
        }

        @Test
        void aFailedReviewOutranksTheFindingsItDidManageToReturn() {
            // The three reasons call for opposite fixes; a partial review that also raised a blocker is an instrument failure, not a surface-map gap.
            SpecFidelityReport partialReview = new SpecFidelityReport(List.of(finding(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE, "the reviewer stopped"),
                    finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "weak")));

            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(partialReview)).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
            assertThat(RepairRoundScheduler.hasPrimaryReviewUnavailableFinding(partialReview)).isTrue();
            assertThat(RepairRoundScheduler.hasInstrumentUnavailableFinding(partialReview)).isTrue();
        }

        @Test
        void unavailableExecutableEvidenceBlocksConvergenceWithoutMasqueradingAsAPrimaryReviewFailure() {
            SpecFidelityReport executableEvidence = SpecFidelityReport.executableEvidenceUnavailable("witness adjudication did not complete");

            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(executableEvidence)).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
            assertThat(RepairRoundScheduler.hasPrimaryReviewUnavailableFinding(executableEvidence)).isFalse();
            assertThat(RepairRoundScheduler.hasInstrumentUnavailableFinding(executableEvidence)).isTrue();
        }

        @Test
        void aConceptAdmissionFindingAloneIsReportedAsConceptAdmittedWithFindings() {
            // The run proceeded with the least-rejected candidate rather than producing nothing; the instructor needs to know that is why nothing was scheduled.
            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(report(SpecFidelityReport.Kind.CONCEPT_ADMISSION_FINDING)))
                    .isEqualTo(TerminationReason.CONCEPT_ADMITTED_WITH_FINDINGS);
        }

        @Test
        void aConceptAdmissionFindingOutranksAnotherUnschedulableBlocker() {
            // A contested concept is upstream of the specification, tests and statement that instantiate it, so it is the cause rather than a second symptom.
            SpecFidelityReport report = report(SpecFidelityReport.Kind.CONCEPT_ADMISSION_FINDING, SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE);

            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(report)).isEqualTo(TerminationReason.CONCEPT_ADMITTED_WITH_FINDINGS);
        }

        @Test
        void anInstrumentUnavailableFindingOutranksAConceptAdmissionFinding() {
            // A review that never ran cannot be reported as a design decision the instructor can act on.
            SpecFidelityReport report = new SpecFidelityReport(List.of(finding(SpecFidelityReport.Kind.CONCEPT_ADMISSION_FINDING, "the central interaction is too shallow"),
                    finding(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE, "the reviewer stopped")));

            assertThat(RepairRoundScheduler.reasonForUnschedulableReport(report)).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
        }
    }

    @Nested
    class FindingDrainAccounting {

        private final RepairRoundScheduler scheduler = new RepairRoundScheduler(UNBOUNDED_BUDGET);

        private ExerciseGenerationRepairRoundDTO round(int attempt, SpecFidelityReport.Finding... findings) {
            return scheduler.recordReviewRound(new SpecFidelityReport(List.of(findings)), attempt);
        }

        @Test
        void theFirstRoundHasNothingToCarryOverOrDrain() {
            ExerciseGenerationRepairRoundDTO first = round(1, finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes"));

            assertThat(first.round()).isEqualTo(1);
            assertThat(first.attempt()).isEqualTo(1);
            assertThat(first.fresh()).isEqualTo(1);
            assertThat(first.carriedOver()).isZero();
            assertThat(first.drained()).isZero();
        }

        @Test
        void roundsAreNumberedInSequenceAndKeepTheirAttemptsApart() {
            // The round index counts completed reviews and the attempt counts authoring passes; they diverge when a mechanically rejected attempt is never reviewed.
            assertThat(round(1).round()).isEqualTo(1);
            assertThat(round(4).round()).isEqualTo(2);
            assertThat(round(4).attempt()).isEqualTo(4);
        }

        @Test
        void blockingAndAdvisoryFindingsAreCountedApart() {
            ExerciseGenerationRepairRoundDTO first = round(1, finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes"),
                    finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, "the statement contradicts the spec"),
                    finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, "no worked example"));

            assertThat(first.blocking()).isEqualTo(2);
            assertThat(first.advisory()).isEqualTo(1);
        }

        @Test
        void anUnchangedFindingIsCarriedOverRatherThanCountedTwice() {
            round(1, finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes"));

            ExerciseGenerationRepairRoundDTO second = round(2, finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes"));

            assertThat(second.carriedOver()).isEqualTo(1);
            assertThat(second.fresh()).isZero();
            assertThat(second.drained()).isZero();
        }

        @Test
        void aRepairedFindingIsDrainedAndAReplacementIsFresh() {
            round(1, finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes"));

            ExerciseGenerationRepairRoundDTO second = round(2, finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "the starter has no anchor"));

            assertThat(second.drained()).isEqualTo(1);
            assertThat(second.fresh()).isEqualTo(1);
            assertThat(second.carriedOver()).isZero();
        }

        @Test
        void aRewrittenDetailDoesNotMakeTheSameDefectLookFresh() {
            // Detail is prose the reviewer rewrites freely while the defect does not move; hashing it would report every finding as fresh and make drain unmeasurable.
            scheduler.recordReviewRound(new SpecFidelityReport(
                    List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes", "the test only checks size"))), 1);

            ExerciseGenerationRepairRoundDTO second = scheduler.recordReviewRound(new SpecFidelityReport(List.of(
                    new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes", "an entirely differently worded explanation"))),
                    2);

            assertThat(second.carriedOver()).isEqualTo(1);
            assertThat(second.fresh()).isZero();
        }

        @Test
        void casingAndPunctuationDoNotMakeTheSameDefectLookFresh() {
            round(1, finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "CJK graphemes are not counted!"));

            ExerciseGenerationRepairRoundDTO second = round(2, finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "  cjk   graphemes are not counted  "));

            assertThat(second.carriedOver()).isEqualTo(1);
            assertThat(second.drained()).isZero();
        }

        @Test
        void aCitedSpecificationRuleLabelDoesNotMakeTheSameDefectLookFresh() {
            // The reviewer cites the spec's own rule label for a defect only about half the time, so "R3 reverse must handle the empty string" and "reverse must handle the empty
            // string" are one defect stated twice. Without stripping the label, half the rounds would report a drain and a fresh finding that never happened.
            round(1, finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "R3 reverse must handle the empty string"));

            ExerciseGenerationRepairRoundDTO second = round(2, finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "reverse must handle the empty string"));

            assertThat(second.carriedOver()).isEqualTo(1);
            assertThat(second.drained()).isZero();
            assertThat(second.fresh()).isZero();
        }

        @Test
        void theSameRequirementUnderADifferentKindIsADifferentDefect() {
            // A requirement that is uncovered and a requirement whose oracle is weak call for different repairs, so collapsing them would report a repair that never happened.
            round(1, finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "empty input is rejected"));

            ExerciseGenerationRepairRoundDTO second = round(2, finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "empty input is rejected"));

            assertThat(second.carriedOver()).isZero();
            assertThat(second.drained()).isEqualTo(1);
            assertThat(second.fresh()).isEqualTo(1);
        }

        @Test
        void aRephrasedFindingIsKnowinglyCountedAsOneDrainedAndOneFresh() {
            // The cost of exact matching, pinned so the overstated drain stays a known quantity: no similarity threshold can be calibrated before the reviewer's own
            // stability is measured.
            round(1, finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "surrogate pairs are counted as two characters"));

            ExerciseGenerationRepairRoundDTO second = round(2, finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "a surrogate pair counts as two"));

            assertThat(second.carriedOver()).isZero();
            assertThat(second.drained()).isEqualTo(1);
            assertThat(second.fresh()).isEqualTo(1);
        }

        @Test
        void twoRequirementsThatDifferOnlyBeyondTheIdentityBoundAreOneIdentity() {
            // The bound keeps a reviewer that emits a whole paragraph as a "requirement" from growing the identity set without limit; past it, the tail stops distinguishing.
            String sharedPrefix = "x".repeat(300);
            round(1, finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, sharedPrefix + "alpha"));

            ExerciseGenerationRepairRoundDTO second = round(2, finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, sharedPrefix + "beta"));

            assertThat(second.carriedOver()).isEqualTo(1);
            assertThat(second.fresh()).isZero();
        }

        @Test
        void twoIdenticalFindingsInOneRoundAreOneIdentityButTwoFindings() {
            // A duplicate must not double the fresh count, or a round would appear to have found twice the work it did; the blocking count is a finding count and keeps it.
            ExerciseGenerationRepairRoundDTO first = round(1, finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes"),
                    finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes"));

            assertThat(first.fresh()).isEqualTo(1);
            assertThat(first.blocking()).isEqualTo(2);
        }

        @Test
        void anEmptyReviewDrainsEverythingTheRoundBeforeItFound() {
            round(1, finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes"),
                    finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "the starter has no anchor"));

            ExerciseGenerationRepairRoundDTO second = round(2);

            assertThat(second.drained()).isEqualTo(2);
            assertThat(second.fresh()).isZero();
            assertThat(second.carriedOver()).isZero();
        }
    }

    @Nested
    class RoundMessages {

        private static ExerciseGenerationRepairRoundDTO round(int index, int carriedOver, int drained, int fresh) {
            return new ExerciseGenerationRepairRoundDTO(index, index, carriedOver + fresh, 0, carriedOver, drained, fresh);
        }

        @Test
        void aCleanRoundSaysSoWithoutQuotingZeroes() {
            assertThat(RepairRoundScheduler.roundMessage(round(3, 0, 2, 0))).isEqualTo("Quality review round 3: no issues remain.");
        }

        @Test
        void theFirstRoundHasNoPreviousRoundToCompareAgainst() {
            // Reporting "0 still open from the previous round, 0 resolved" would invite an instructor to read a comparison that does not exist.
            assertThat(RepairRoundScheduler.roundMessage(round(1, 0, 0, 2))).isEqualTo("Quality review round 1: 2 issues found.");
            assertThat(RepairRoundScheduler.roundMessage(round(1, 0, 0, 1))).isEqualTo("Quality review round 1: 1 issue found.");
        }

        @Test
        void aLaterRoundReportsTheDrainRatherThanJustTheTotal() {
            // "3 issues" alone cannot tell a stuck repair loop from a productive one.
            assertThat(RepairRoundScheduler.roundMessage(round(2, 1, 4, 2)))
                    .isEqualTo("Quality review round 2: 3 issues — 1 still open from the previous round, 4 resolved, 2 new.");
        }
    }

    @Nested
    class RepairProgress {

        @Test
        void requiresARepairToRemoveABlockerWithoutAddingAnother() {
            SpecFidelityReport first = new SpecFidelityReport(List.of(finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, "contradictory boundary"),
                    finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "missing starter guidance")));
            SpecFidelityReport improved = new SpecFidelityReport(List.of(finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "missing starter guidance")));
            SpecFidelityReport renamed = new SpecFidelityReport(List.of(finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "missing starter guidance"),
                    finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, "different contradiction")));

            assertThat(RepairRoundScheduler.repairImproved(first, improved)).isTrue();
            assertThat(RepairRoundScheduler.repairImproved(first, first)).isFalse();
            assertThat(RepairRoundScheduler.repairImproved(first, renamed)).isFalse();
        }

        @Test
        void ignoresAdvisoriesAndUnavailableInstrumentsWhenJudgingTheCandidate() {
            SpecFidelityReport first = report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE);
            SpecFidelityReport current = new SpecFidelityReport(List.of(finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, "add an example"),
                    SpecFidelityReport.executableEvidenceUnavailable("probe failed").findings().getFirst()));

            assertThat(RepairRoundScheduler.repairImproved(first, current)).isTrue();
        }

        @Test
        void rejectsARepairThatIntroducesTheFirstCandidateBlocker() {
            assertThat(RepairRoundScheduler.repairImproved(SpecFidelityReport.empty(), report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION))).isFalse();
        }

        @Test
        void rejectsARepairThatTradesARepairableBlockerForAnUnschedulableOne() {
            SpecFidelityReport first = report(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP);
            SpecFidelityReport current = report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION);

            assertThat(RepairRoundScheduler.repairImproved(first, current)).isFalse();
        }
    }
}
