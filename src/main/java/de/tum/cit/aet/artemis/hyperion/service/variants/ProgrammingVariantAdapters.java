package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.List;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;

/**
 * Capability adapters for programming-exercise variants (plan Sections 2.7.1 and 3, Student A focus).
 * Thin wrappers around existing, battle-tested services — very little new logic (Section 2.3).
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ProgrammingVariantAdapters implements VariantTypeAdapters {

    // TODO (Opus): Inject via constructor (plan Section 3 table, "Existing code reused" column):
    // - HyperionProgrammingExerciseContextRendererService (ANALYZING)
    // - ProgrammingExerciseImportService + ProgrammingExerciseValidationService (PROVISIONING)
    // - GitService + RepositoryService (TRANSFORMING toolset: checkout, read, edit, commit)
    // - ContinuousIntegrationTriggerService + VariantBuildVerificationService (runBuild / VERIFYING)
    // - HyperionConsistencyCheckService (semantic gate, Section 2.6 step 3)
    // - the service-level variant-group assignment path behind ExerciseVariantGroupResource (FINALIZING)
    // - ProgrammingExerciseRepository (reload with template/solution participations)

    @Override
    public ExerciseType supportedExerciseType() {
        return ExerciseType.PROGRAMMING;
    }

    @Override
    public String renderContext(Exercise source) {
        // TODO (Sonnet): Cast to ProgrammingExercise and delegate to
        // HyperionProgrammingExerciseContextRendererService.renderContext(exercise) — problem statement +
        // template/solution/test repo contents (plan Section 3, ANALYZING row). No new rendering logic.
        throw new UnsupportedOperationException("TODO (Sonnet): plan Section 3, ANALYZING row");
    }

    @Override
    public Exercise provision(Exercise source, VariantGenerationRequestDTO request, VariantJob job) {
        // TODO (Opus): See ExerciseProvisioner Javadoc — importProgrammingExercise clone (entity + repos + build
        // plans), title from job.getChangePlan().variantTitle(), deterministic short-name/project-key derivation,
        // suffix retry (-V2, -V3) re-running checkIfProjectExists on collision (plan Section 3 PROVISIONING row,
        // Section 6 row 1). Return the persisted clone.
        throw new UnsupportedOperationException("TODO (Opus): plan Section 3, PROVISIONING row");
    }

    @Override
    public List<ToolCallback> createTools(Exercise variant, VariantJob job) {
        // TODO (Opus): Build the programming toolset per VariantToolsetFactory Javadoc / plan Section 2.5 table:
        // listFiles, readFile, applyEdit/writeFile (search-replace edits, validation errors back to the model),
        // runBuild (commit+push+trigger+wait via VariantBuildVerificationService), getBuildAndTestResults
        // (compiler output + failed test names/messages), finish(summary). Tools operate ONLY on the variant's
        // repos. Track test-repo edits for the build-dependency constraint (Section 3).
        throw new UnsupportedOperationException("TODO (Opus): plan Section 2.5 toolset table");
    }

    @Override
    public VerificationReport verify(Exercise variant, ChangePlan plan) {
        // TODO (Opus): Gate 1: solution build passes 100% AND template build fails, via
        // VariantBuildVerificationService (hasReachedTargetResult per RepositoryType, plan Section 2.6 step 1).
        // CI timeout → failed attempt with distinct detail (BuildResultState.TIMED_OUT, Section 6). Gate 3:
        // HyperionConsistencyCheckService semantic gate incl. ChangePlan invariants ("test names referenced in the
        // problem statement exist in the test repo", Sections 2.4/2.6). Return structured findings.
        throw new UnsupportedOperationException("TODO (Opus): plan Section 2.6");
    }

    @Override
    public void finalizeVariant(Exercise variant, VariantGenerationRequestDTO request) {
        // TODO (Sonnet): Persist final problem statement; then shared placement logic per VariantFinalizer Javadoc —
        // EXISTING_GROUP / NEW_GROUP / STANDALONE via the ExerciseVariantGroupResource service path, or
        // SAME_EXAM_GROUP when variant.isExamExercise() (plan Sections 3 FINALIZING row and 5.5). Consider extracting
        // the placement logic into a shared package-private helper both adapter bundles call (Section 8: "Finalizer:
        // shared implementation already works").
        throw new UnsupportedOperationException("TODO (Sonnet): plan Section 3, FINALIZING row");
    }
}
