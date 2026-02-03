package de.tum.cit.aet.artemis.modeling.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;

/**
 * DTO containing {@link Result} information for modeling assessments.
 * This does not include large reference attributes in order to send minimal data to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, SubmissionDTO submission, ParticipationDTO participation,
        List<FeedbackDTO> feedbacks, AssessmentType assessmentType, Boolean hasComplaint, Boolean exampleResult) implements Serializable {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FeedbackDTO(Long id, String text, String detailText, Boolean hasLongFeedbackText, String reference, Double credits, Boolean positive, FeedbackType type,
            Visibility visibility) implements Serializable {

        public static FeedbackDTO of(Feedback feedback) {
            return new FeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(),
                    feedback.getCredits(), feedback.isPositive(), feedback.getType(), feedback.getVisibility());
        }
    }

    public static ResultDTO of(Result result) {
        return of(result, result.getFeedbacks());
    }

    /**
     * Converts a Result into a ResultDTO
     *
     * @param result           to convert
     * @param filteredFeedback feedback that should get sent to the client, will get converted into {@link FeedbackDTO} objects.
     * @return the converted DTO
     */
    public static ResultDTO of(Result result, List<Feedback> filteredFeedback) {
        SubmissionDTO submissionDTO = null;
        ParticipationDTO participationDTO = null;
        if (Hibernate.isInitialized(result.getSubmission()) && result.getSubmission() != null) {
            submissionDTO = SubmissionDTO.of(result.getSubmission(), false, null, null);
            participationDTO = ParticipationDTO.of(result.getSubmission().getParticipation());
        }
        var feedbackDTOs = filteredFeedback.stream().map(FeedbackDTO::of).toList();
        return new ResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), submissionDTO, participationDTO, feedbackDTOs,
                result.getAssessmentType(), result.hasComplaint(), result.isExampleResult());
    }
}
