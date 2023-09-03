package de.tum.in.www1.artemis.service.dto.athena;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;

/**
 * A DTO representing a ProgrammingSubmission, for transferring data to Athena
 */
public record ProgrammingSubmissionDTO(long id, long exerciseId, String repositoryUrl) {

    @Value("${server.url}")
    private static String artemisServerUrl;

    /**
     * Creates a new ProgrammingSubmissionDTO from a ProgrammingSubmission. The DTO also contains the exerciseId of the exercise the submission belongs to.
     *
     * @param exerciseId The id of the exercise the submission belongs to
     * @param submission The submission to create the DTO from
     * @return The created DTO
     */
    public static ProgrammingSubmissionDTO of(long exerciseId, @NotNull ProgrammingSubmission submission) {
        return new ProgrammingSubmissionDTO(submission.getId(), exerciseId,
                artemisServerUrl + "/api/public/athena/programming-exercises/" + exerciseId + "/submissions/" + submission.getId() + "/repository");
    }
}
