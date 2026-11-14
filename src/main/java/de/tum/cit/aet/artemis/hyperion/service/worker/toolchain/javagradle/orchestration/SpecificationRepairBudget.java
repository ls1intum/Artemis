package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.StageCheckResult.SpecificationCheck;

/** Lets proven mechanical progress use spare job turns without spending the semantic review allowance or starving downstream authoring. */
final class SpecificationRepairBudget {

    private static final int INITIAL_REFINEMENTS = 3;

    private static final int STALLED_LIMIT = 3;

    private static final int REFINEMENT_TURNS = 5;

    private int refinements;

    private int stalled;

    @Nullable
    private SpecificationCheck furthestCheck;

    void acceptedSpecification() {
        furthestCheck = null;
        stalled = 0;
    }

    int allocate(StageCheckResult gate, int ordinaryPool, int surplusPool) {
        SpecificationCheck check = gate.specificationCheck();
        if (check != null && (furthestCheck == null || check.ordinal() > furthestCheck.ordinal())) {
            furthestCheck = check;
            stalled = 0;
        }
        else {
            stalled++;
        }
        boolean initial = refinements < INITIAL_REFINEMENTS;
        int available = initial ? ordinaryPool : surplusPool;
        if ((!initial && (furthestCheck == null || stalled >= STALLED_LIMIT)) || available < StagedGenerationRunner.MIN_STAGE_BUDGET) {
            return 0;
        }
        refinements++;
        return StagedGenerationRunner.allocateStageBudget(REFINEMENT_TURNS, 0, available);
    }
}
