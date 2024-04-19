package de.tum.in.www1.artemis.service.dto.athena;

import jakarta.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;

/**
 * A DTO representing a ProgrammingSubmission, for transferring data to Athena
 */
public record ProgrammingSubmissionDTO(long id, long exerciseId, String repositoryUri) implements SubmissionDTO {

    /**
     * Creates a new ProgrammingSubmissionDTO from a ProgrammingSubmission. The DTO also contains the exerciseId of the exercise the submission belongs to.
     *
     * @param exerciseId The id of the exercise the submission belongs to
     * @param submission The submission to create the DTO from
     * @return The created DTO
     */
    public static ProgrammingSubmissionDTO of(long exerciseId, @NotNull ProgrammingSubmission submission, String artemisServerUrl) {
        return new ProgrammingSubmissionDTO(submission.getId(), exerciseId,
                artemisServerUrl + "/api/public/athena/programming-exercises/" + exerciseId + "/submissions/" + submission.getId() + "/repository");
    }
}
