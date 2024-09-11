package de.tum.cit.aet.artemis.service.dto.athena;

import static de.tum.cit.aet.artemis.config.Constants.ATHENA_PROGRAMMING_EXERCISE_REPOSITORY_API_PATH;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;

/**
 * A DTO representing a ProgrammingSubmission, for transferring data to Athena
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingSubmissionDTO(long id, long exerciseId, String repositoryUri) implements SubmissionBaseDTO {

    /**
     * Creates a new ProgrammingSubmissionDTO from a ProgrammingSubmission. The DTO also contains the exerciseId of the exercise the submission belongs to.
     *
     * @param exerciseId The id of the exercise the submission belongs to
     * @param submission The submission to create the DTO from
     * @return The created DTO
     */
    public static ProgrammingSubmissionDTO of(long exerciseId, @NotNull ProgrammingSubmission submission, String artemisServerUrl) {
        return new ProgrammingSubmissionDTO(submission.getId(), exerciseId,
                artemisServerUrl + ATHENA_PROGRAMMING_EXERCISE_REPOSITORY_API_PATH + exerciseId + "/submissions/" + submission.getId() + "/repository");
    }
}
