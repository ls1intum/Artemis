package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;

/**
 * Capability interface: clones the source exercise into the variant-to-be during PROVISIONING
 * (plan Section 2.3). Handles title/short-name uniqueness deterministically — never via the LLM
 * (plan Section 6, row 1).
 */
public interface ExerciseProvisioner {

    /**
     * Creates the variant clone.
     *
     * TODO (Opus, programming): implement via ProgrammingExerciseImportService.importProgrammingExercise(...)
     * — clones entity + repos + build plans. Set title from job.changePlan().variantTitle(); derive the short
     * name/project key deterministically from it; validate with
     * ProgrammingExerciseValidationService.validateNewProgrammingExerciseSettings / checkIfProjectExists and on
     * collision retry with suffix (-V2, -V3, ...) re-running checkIfProjectExists each time (plan Section 3
     * PROVISIONING row, Section 6 row 1).
     *
     * TODO (Sonnet, quiz): implement via QuizExerciseImportService.importQuizExercise(...) — deep-copies the quiz
     * incl. DnD images (copyDragItemFile), mappings, and batches (plan Section 4, PROVISIONING row). Title from the
     * ChangePlan as above; quizzes have no project key, so collision handling reduces to title uniqueness if enforced.
     *
     * TODO (Opus): On any provisioning exception, the pipeline deletes the half-created exercise via the existing
     * exercise deletion service (repos + build plans cleaned up) and the job goes to FAILED (plan Section 6,
     * "Hard failure before exercise exists" row) — the provisioner itself should throw, cleanup is the pipeline's job.
     *
     * @param source  the source exercise
     * @param request the wizard request (placement is NOT applied here — that is the finalizer's job, Section 2.3)
     * @param job     the running job — provisioners read the ChangePlan (title) from it and the pipeline stores the
     *                    provisioned exercise id on it afterwards
     * @return the persisted variant exercise (not yet transformed)
     */
    Exercise provision(Exercise source, VariantGenerationRequestDTO request, VariantJob job);
}
