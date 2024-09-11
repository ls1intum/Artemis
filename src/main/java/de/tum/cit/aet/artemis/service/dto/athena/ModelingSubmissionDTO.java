package de.tum.cit.aet.artemis.service.dto.athena;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;

/**
 * A DTO representing a ModelingSubmission, for transferring data to Athena
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingSubmissionDTO(long id, long exerciseId, String model) implements SubmissionBaseDTO {

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
