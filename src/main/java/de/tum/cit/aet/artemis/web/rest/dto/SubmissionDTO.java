package de.tum.cit.aet.artemis.web.rest.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * DTO containing {@link Submission} information.
 * This does not include large reference attributes in order to send minimal data to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionDTO(Long id, Boolean submitted, SubmissionType type, Boolean exampleSubmission, ZonedDateTime submissionDate, String commitHash, Boolean buildFailed,
        Boolean buildArtifact, ParticipationDTO participation, String submissionExerciseType) implements Serializable {

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
