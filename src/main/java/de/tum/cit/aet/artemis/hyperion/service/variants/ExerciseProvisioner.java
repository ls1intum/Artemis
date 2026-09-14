package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;

/**
 * Capability interface: clones the source exercise into the variant-to-be during PROVISIONING.
 * Handles title/short-name uniqueness deterministically — never via the LLM.
 */
public interface ExerciseProvisioner {

    /**
     * Creates the variant clone. Programming clones entity + repos + build plans via
     * {@code ProgrammingExerciseImportService} and derives a unique short name/project key from the ChangePlan
     * title with suffix retry on collision; quiz deep-copies the quiz (incl. DnD images, mappings, batches) via
     * {@code QuizExerciseImportService}. On any provisioning failure the provisioner throws and the pipeline
     * deletes the half-created exercise.
     *
     * @param source  the source exercise
     * @param request the wizard request (placement is NOT applied here — that is the finalizer's job)
     * @param job     the running job — provisioners read the ChangePlan (title) from it and the pipeline stores the
     *                    provisioned exercise id on it afterwards
     * @return the persisted variant exercise (not yet transformed)
     */
    Exercise provision(Exercise source, VariantGenerationRequestDTO request, VariantJob job);
}
