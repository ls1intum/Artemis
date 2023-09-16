package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;

/**
 * DTO containing {@link Submission} information.
 * This does not include large reference attributes in order to send minimal data to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionDTO(Long id, Boolean submitted, SubmissionType type, Boolean exampleSubmission, ZonedDateTime submissionDate, String commitHash, Boolean buildFailed,
        Boolean buildArtifact, ParticipationDTO participation, String submissionExerciseType) {

    /**
     * Converts a Submission into a SubmissionDTO.
     *
     * @param submission to convert
     * @return the converted DTO
     */
    public static SubmissionDTO of(Submission submission) {
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            // For programming submissions we need to extract additional information (e.g. the commit hash) and send it to the client
            return new SubmissionDTO(programmingSubmission.getId(), programmingSubmission.isSubmitted(), programmingSubmission.getType(),
                    programmingSubmission.isExampleSubmission(), programmingSubmission.getSubmissionDate(), programmingSubmission.getCommitHash(),
                    programmingSubmission.isBuildFailed(), programmingSubmission.isBuildArtifact(), ParticipationDTO.of(programmingSubmission.getParticipation()),
                    programmingSubmission.getSubmissionExerciseType());
        }
        return new SubmissionDTO(submission.getId(), submission.isSubmitted(), submission.getType(), submission.isExampleSubmission(), submission.getSubmissionDate(), null, null,
                null, ParticipationDTO.of(submission.getParticipation()), submission.getSubmissionExerciseType());
    }
}
