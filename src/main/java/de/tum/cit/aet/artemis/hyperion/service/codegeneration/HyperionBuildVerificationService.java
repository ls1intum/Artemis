package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Shared build-verification helper used by BOTH code generation and variant generation
 * (plan Section 3, "Refactor note" + Section 9 Day 1 AM). Day-1 refactor task: this is a PURE EXTRACTION —
 * move logic out of {@code HyperionCodeGenerationExecutionService}, do not copy-paste the polling loop,
 * and keep the existing code-generation tests green.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionBuildVerificationService {

    // TODO (Opus): PURE REFACTOR (plan Section 9, D1 AM — do this FIRST, before any variant logic):
    // 1. Move the following private members from HyperionCodeGenerationExecutionService into this service as
    // public methods, unchanged in behavior:
    // - waitUntilRemoteHasCommit(...) (polls until the pushed commit is visible remotely)
    // - waitForBuildResult(...) (polls the build result for a participation/repository)
    // - hasReachedTargetResult(...) (encodes "solution must pass / template must fail" per RepositoryType —
    // exactly the variant verification rule, plan Section 2.6 step 1)
    // - the BuildResultOutcome type they share (move or make it a top-level/public nested type here)
    // 2. Rewire HyperionCodeGenerationExecutionService to inject and delegate to this service.
    // 3. Preserve timeout semantics incl. BuildResultState.TIMED_OUT handling (plan Section 6, CI-timeout row).
    // 4. Existing tests (HyperionCodeGenerationExecutionServiceTest) must pass unmodified except for wiring.
    //
    // TODO (Opus): Afterwards, ProgrammingVariantAdapters.verify(...) and the runBuild tool call these public
    // methods (plan Section 3, VERIFYING/REPAIRING row).
}
