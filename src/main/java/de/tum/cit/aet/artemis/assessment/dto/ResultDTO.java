package de.tum.cit.aet.artemis.assessment.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;

/**
 * DTO containing {@link Result} information for the assessment (text) module.
 * This does not include large reference attributes in order to send minimal data to the client.
 * Lazy associations (submission, participation, feedbacks, assessor) are guarded with {@link Hibernate#isInitialized}
 * so uninitialized proxies map to null/empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, SubmissionDTO submission, ParticipationDTO participation,
        List<FeedbackDTO> feedbacks, AssessmentType assessmentType, Integer correctionRound, Boolean hasComplaint, Boolean exampleResult, UserNameDTO assessor,
        AssessmentNoteDTO assessmentNote, Long exerciseId) implements Serializable {

    /**
     * Converts a Result into a ResultDTO.
     *
     * @param result to convert
     * @return the converted DTO, or null if the result is null
     */
    public static ResultDTO of(Result result) {
        if (result == null) {
            return null;
        }
        SubmissionDTO submissionDTO = null;
        ParticipationDTO participationDTO = null;
        if (Hibernate.isInitialized(result.getSubmission()) && result.getSubmission() != null) {
            submissionDTO = SubmissionDTO.of(result.getSubmission(), false, null, null);
            if (Hibernate.isInitialized(result.getSubmission().getParticipation())) {
                participationDTO = ParticipationDTO.of(result.getSubmission().getParticipation());
            }
        }
        List<FeedbackDTO> feedbackDTOs = null;
        if (Hibernate.isInitialized(result.getFeedbacks())) {
            feedbackDTOs = result.getFeedbacks().stream().map(FeedbackDTO::of).toList();
        }
        UserNameDTO assessorDTO = null;
        if (Hibernate.isInitialized(result.getAssessor())) {
            assessorDTO = UserNameDTO.of(result.getAssessor());
        }
        AssessmentNoteDTO assessmentNoteDTO = AssessmentNoteDTO.of(result.getAssessmentNote());
        // exerciseId is a non-null FK column on the result table (denormalized in #11459) that the entity payload always
        // carried. Clients echo loaded results back into entity-typed endpoints (example-submission save, manual
        // results), where the cascade merge writes the column back - without it on the wire the echo merges
        // exercise_id = 0 and dies on the foreign-key constraint.
        return new ResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), submissionDTO, participationDTO, feedbackDTOs,
                result.getAssessmentType(), result.getCorrectionRound(), result.hasComplaint(), result.isExampleResult(), assessorDTO, assessmentNoteDTO, result.getExerciseId());
    }
}
