package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;

/**
 * DTO for a complaint with sensitive information filtered out.
 *
 * <p>
 * This DTO is used to transfer complaint data to the client
 * without exposing the full {@link de.tum.cit.aet.artemis.assessment.domain.Complaint} entity.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComplaintDTO(Long id, String complaintText, ZonedDateTime submittedTime, ComplaintType complaintType, Boolean complaintIsAccepted,
        ComplaintResponseDTO complaintResponse, @NotNull ResultSimpleDTO result) {

    /**
     * DTO containing the minimal information of {@link Result} needed in complaint.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ResultSimpleDTO(Long id, ZonedDateTime completionDate, Double score, Boolean rated, AssessmentType assessmentType, Long submissionId, Long participationId,
            Long exerciseId, String exerciseTitle, List<FeedbackDTO> feedbacks) {

        /**
         * Creates a {@link ResultSimpleDTO} from a {@link Result} entity.
         *
         * @param result the result entity to convert
         * @return the corresponding DTO
         */
        public static ResultSimpleDTO of(Result result) {
            Objects.requireNonNull(result, "The result must be set");

            Long submissionId = null;
            Long participationId = null;
            Long exerciseId = null;
            String exerciseTitle = null;

            if (result.getSubmission() != null && Hibernate.isInitialized(result.getSubmission())) {
                submissionId = result.getSubmission().getId();

                if (result.getSubmission().getParticipation() != null && Hibernate.isInitialized(result.getSubmission().getParticipation())) {
                    participationId = result.getSubmission().getParticipation().getId();

                    if (result.getSubmission().getParticipation().getExercise() != null && Hibernate.isInitialized(result.getSubmission().getParticipation().getExercise())) {
                        exerciseId = result.getSubmission().getParticipation().getExercise().getId();
                        exerciseTitle = result.getSubmission().getParticipation().getExercise().getTitle();
                    }
                }
            }
            List<FeedbackDTO> feedbackDTOs = null;
            if (result.getFeedbacks() != null && Hibernate.isInitialized(result.getFeedbacks())) {
                feedbackDTOs = result.getFeedbacks().stream().filter(Objects::nonNull).map(FeedbackDTO::of).toList();
            }
            return new ResultSimpleDTO(result.getId(), result.getCompletionDate(), result.getScore(), result.isRated(), result.getAssessmentType(), submissionId, participationId,
                    exerciseId, exerciseTitle, feedbackDTOs);
        }

        /**
         * DTO containing the {@link Feedback} information needed in complaint.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public record FeedbackDTO(Long id, String text, String detailText, boolean hasLongFeedbackText, String reference, Double credits, Boolean positive, FeedbackType type,
                Visibility visibility, String testCaseName) {

            /**
             * Creates a {@link FeedbackDTO} from a {@link Feedback} entity.
             *
             * @param feedback the feedback entity to convert
             * @return the corresponding DTO
             */
            public static FeedbackDTO of(Feedback feedback) {
                Objects.requireNonNull(feedback, "The feedback must be set");
                String testCaseName = null;
                if (feedback.getTestCase() != null && Hibernate.isInitialized(feedback.getTestCase())) {
                    testCaseName = feedback.getTestCase().getTestName();
                }
                return new FeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(),
                        feedback.getCredits(), feedback.isPositive(), feedback.getType(), feedback.getVisibility(), testCaseName);
            }
        }
    }

    /**
     * Creates a {@link ComplaintDTO} from a {@link Complaint} entity.
     *
     * @param complaint the complaint entity to convert
     * @return the corresponding DTO
     * @throws NullPointerException if required fields are missing
     */
    public static ComplaintDTO of(Complaint complaint) {
        Objects.requireNonNull(complaint, "The complaint must be set");
        Objects.requireNonNull(complaint.getResult(), "The associated result must exist");

        ResultSimpleDTO resultDTO = ResultSimpleDTO.of(complaint.getResult());
        ComplaintResponseDTO complaintResponseDTO = complaint.getComplaintResponse() != null ? ComplaintResponseDTO.of(complaint.getComplaintResponse()) : null;

        return new ComplaintDTO(complaint.getId(), complaint.getComplaintText(), complaint.getSubmittedTime(), complaint.getComplaintType(), complaint.isAccepted(),
                complaintResponseDTO, resultDTO);
    }
}
