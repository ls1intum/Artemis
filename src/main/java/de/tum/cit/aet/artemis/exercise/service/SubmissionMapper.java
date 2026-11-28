package de.tum.cit.aet.artemis.exercise.service;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/** Maps persisted data to the records exchanged with clients and build agents. */
public final class SubmissionMapper {

    private SubmissionMapper() {
    }

    /**
     * Converts a Submission into a SubmissionDTO.
     *
     * @param submission              submission to convert
     * @param isProcessing            whether the agent is processing this submission
     * @param buildStartDate          start of the current build, if available
     * @param estimatedCompletionDate estimated end of the current build, if available
     * @return the converted DTO
     */
    public static SubmissionDTO of(Submission submission, boolean isProcessing, ZonedDateTime buildStartDate, ZonedDateTime estimatedCompletionDate) {
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            // For programming submissions we need to extract additional information (e.g. the commit hash) and send it to the client
            return new SubmissionDTO(programmingSubmission.getId(), programmingSubmission.isSubmitted(), programmingSubmission.getType(),
                    programmingSubmission.isExampleSubmission(), programmingSubmission.getSubmissionDate(), programmingSubmission.getCommitHash(),
                    programmingSubmission.isBuildFailed(), ParticipationMapper.of(programmingSubmission.getParticipation()), programmingSubmission.getSubmissionExerciseType(),
                    isProcessing, buildStartDate, estimatedCompletionDate);
        }
        return new SubmissionDTO(submission.getId(), submission.isSubmitted(), submission.getType(), submission.isExampleSubmission(), submission.getSubmissionDate(), null, null,
                ParticipationMapper.of(submission.getParticipation()), submission.getSubmissionExerciseType(), false, null, null);
    }
}
