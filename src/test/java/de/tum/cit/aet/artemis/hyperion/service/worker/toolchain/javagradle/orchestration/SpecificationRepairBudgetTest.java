package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageCheckResult.SpecificationCheck;

class SpecificationRepairBudgetTest {

    @Test
    void laterProvenChecksCanUseSurplusWithoutSpendingDownstreamTurns() {
        var budget = new SpecificationRepairBudget();
        for (var check : new SpecificationCheck[] { SpecificationCheck.SECTIONS, SpecificationCheck.DESIGN, SpecificationCheck.PUBLIC_API, SpecificationCheck.WEIGHTS }) {
            assertThat(budget.allocate(StageCheckResult.failedSpecification(check, "current gate"), 100, 39)).isEqualTo(5);
        }
        assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.LEARNER_OWNERS, "new gate"), 63, 2)).isZero();
    }

    @Test
    void rephrasingOrRegressingARepeatedFailureDoesNotResetTheStallLimit() {
        var budget = new SpecificationRepairBudget();
        assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.WEIGHTS, "first"), 100, 39)).isPositive();
        assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.WEIGHTS, "rephrased"), 100, 39)).isPositive();
        assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.SECTIONS, "regressed"), 100, 39)).isPositive();
        assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.WEIGHTS, "another wording"), 100, 39)).isZero();
    }

    @Test
    void unknownProgressKeepsTheExistingBoundedAllowance() {
        var budget = new SpecificationRepairBudget();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(budget.allocate(StageCheckResult.failed("unclassified"), 100, 39)).isPositive();
        }
        assertThat(budget.allocate(StageCheckResult.failed("different unclassified wording"), 100, 39)).isZero();
    }

    @Test
    void anOrdinaryCorrectionStillFitsInsideTheOriginalSmallPool() {
        var budget = new SpecificationRepairBudget();
        assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.SECTIONS, "missing"), 3, -58)).isEqualTo(3);
        assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.DESIGN, "missing"), 2, -59)).isZero();
    }

    @Test
    void aSemanticRevisionGetsFreshProgressTrackingButCannotRenewTheInitialTurnAllowance() {
        var budget = new SpecificationRepairBudget();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.LEARNER_OWNERS, "original contract"), 100, 39)).isPositive();
        }
        budget.acceptedSpecification();
        assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.SECTIONS, "revised contract"), 100, 39)).isPositive();
        assertThat(budget.allocate(StageCheckResult.failedSpecification(SpecificationCheck.DESIGN, "revised contract"), 63, 2)).isZero();
    }

}
