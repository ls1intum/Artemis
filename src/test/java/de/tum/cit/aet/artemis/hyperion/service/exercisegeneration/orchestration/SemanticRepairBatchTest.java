package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;

/**
 * The repair-round scheduler is a pure function of the review findings and the fairness state, so it is covered here rather than through a generation run: driving it through the
 * orchestrator would pay a full agent-loop fixture for every scheduling rule and could only observe the choice indirectly, through the prompt.
 */
class SemanticRepairBatchTest {

    private static SpecFidelityReport oracleAndScaffoldFindings() {
        // Blocking findings on two surfaces at once, which is what makes the scheduler's choice observable.
        return new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes", "..."),
                new SpecFidelityReport.Finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "starter has no anchor", "...")));
    }

    @Test
    void aSurfaceMayHoldConsecutiveRoundsBecauseStrengtheningOneCanTakeSeveral() {
        // Strengthening an oracle until it rejects every contract-breaking implementation can genuinely take several rounds, so yielding after one would cost the round that
        // closes the last gap.
        SpecFidelityReport report = oracleAndScaffoldFindings();

        assertThat(SemanticRepairBatch.next(report, EnumSet.noneOf(RepairSurface.class), null, 0).orElseThrow().surface()).isEqualTo(RepairSurface.ORACLE);
        assertThat(SemanticRepairBatch.next(report, EnumSet.of(RepairSurface.ORACLE), RepairSurface.ORACLE, 1).orElseThrow().surface()).isEqualTo(RepairSurface.ORACLE);
    }

    @Test
    void aSurfaceThatHasHeldTooLongYieldsToOneNeverRepaired() {
        // Otherwise the scaffold findings above sit unscheduled across consecutive rounds and ship unrepaired.
        SpecFidelityReport report = oracleAndScaffoldFindings();

        SemanticRepairBatch batch = SemanticRepairBatch.next(report, EnumSet.of(RepairSurface.ORACLE), RepairSurface.ORACLE, 2).orElseThrow();

        assertThat(batch.surface()).isEqualTo(RepairSurface.SCAFFOLD);
        assertThat(batch.report().findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP));
    }

    @Test
    void aSurfaceKeepsWorkingWhenEveryOtherSurfaceIsAlreadyClean() {
        // Yielding is only meaningful when something is waiting. With nothing else outstanding the leading surface continues rather than stalling the budget.
        SpecFidelityReport oracleOnly = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes", "...")));

        assertThat(SemanticRepairBatch.next(oracleOnly, EnumSet.of(RepairSurface.ORACLE), RepairSurface.ORACLE, 5).orElseThrow().surface()).isEqualTo(RepairSurface.ORACLE);
    }

    @Test
    void repairSchedulingStillCarriesOnlyTheScheduledSurfacesFindings() {
        // Repairs stay causally scoped: one repair is never handed every artifact's findings at once.
        SemanticRepairBatch batch = SemanticRepairBatch.next(oracleAndScaffoldFindings(), EnumSet.noneOf(RepairSurface.class), null, 0).orElseThrow();

        assertThat(batch.report().findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE));
    }

    @Test
    void anOracleRepairAlsoCarriesEnvironmentValidatedWitnesses() {
        SpecFidelityReport report = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes", "environment-proven"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE, "an executable boundary witness", "reference passes and starter fails"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "starter has no anchor", "separate surface")));

        SemanticRepairBatch batch = SemanticRepairBatch.next(report, EnumSet.noneOf(RepairSurface.class), null, 0).orElseThrow();

        assertThat(batch.surface()).isEqualTo(RepairSurface.ORACLE);
        assertThat(batch.report().findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE,
                SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE);
    }

    @Test
    void aTextOnlyOracleSuspicionDoesNotDriveAutonomousRepair() {
        SpecFidelityReport staticReview = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "a reviewer suspects a gap", "no executable evidence")));

        assertThat(staticReview.hasBlockingFindings()).isFalse();
        assertThat(SemanticRepairBatch.next(staticReview, EnumSet.noneOf(RepairSurface.class), null, 0)).isEmpty();
    }
}
