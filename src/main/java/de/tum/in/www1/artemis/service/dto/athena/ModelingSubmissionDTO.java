package de.tum.in.www1.artemis.service.dto.athena;

import jakarta.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;

/**
 * A DTO representing a ModelingSubmission, for transferring data to Athena
 */
public record ModelingSubmissionDTO(long id, long exerciseId, String model) implements SubmissionDTO {

    /**
     * Creates a new ModelingSubmissionDTO from a ModelingSubmission. The DTO also contains the exerciseId of the exercise the submission belongs to.
     *
     * @param exerciseId The id of the exercise the submission belongs to
     * @param submission The submission to create the DTO from
     * @return The created DTO
     */
    public static ModelingSubmissionDTO of(long exerciseId, @NotNull ModelingSubmission submission) {
        return new ModelingSubmissionDTO(submission.getId(), exerciseId, submission.getModel());
    }
}
