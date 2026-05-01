package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionWithParticipationDTO;

/**
 * DTO for transferring complaint data to the client.
 *
 * <p>
 * This DTO represents a reduced view of the {@link Complaint} entity and is intended
 * to be used for serialization over the API.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComplaintDTO(Long id, String complaintText, ZonedDateTime submittedTime, ComplaintType complaintType, Boolean complaintIsAccepted,
        ComplaintResponseDTO complaintResponse, ResultSimpleDTO result, ParticipantDTO participant) {

    /**
     * DTO containing the minimal information of the participant needed in the complaint.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParticipantDTO(Long id, String name,
            @Pattern(regexp = Constants.LOGIN_REGEX) @Size(min = Constants.USERNAME_MIN_LENGTH, max = Constants.USERNAME_MAX_LENGTH) String login, Boolean isStudent) {
    }

    /**
     * DTO containing the minimal information of {@link Result} needed in complaint.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResultSimpleDTO(Long id, ZonedDateTime completionDate, Double score, Boolean rated, AssessmentType assessmentType, SubmissionWithParticipationDTO submission,
            UserPublicInfoDTO assessor, List<FeedbackDTO> feedbacks, String exerciseTitle) {

        /**
         * DTO containing the {@link Feedback} information needed in the result.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record FeedbackDTO(Long id, String text, String detailText, Boolean hasLongFeedbackText, String reference, Double credits, Boolean positive, FeedbackType type,
                Visibility visibility, String testCaseName, GradingInstructionDTO gradingInstruction) {

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
                GradingInstructionDTO gradingInstructionDTO = null;
                if (feedback.getGradingInstruction() != null && Hibernate.isInitialized(feedback.getGradingInstruction())) {
                    gradingInstructionDTO = GradingInstructionDTO.of(feedback.getGradingInstruction());
                }
                return new FeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(),
                        feedback.getCredits(), feedback.isPositive(), feedback.getType(), feedback.getVisibility(), testCaseName, gradingInstructionDTO);
            }
        }

        /**
         * Creates a {@link ResultSimpleDTO} from a {@link Result} entity.
         *
         * @param result the result entity to convert
         * @return the corresponding DTO
         */
        public static ResultSimpleDTO of(Result result) {
            Objects.requireNonNull(result, "The result must be set");
            UserPublicInfoDTO assessor = null;
            if (result.getAssessor() != null && Hibernate.isInitialized(result.getAssessor())) {
                assessor = new UserPublicInfoDTO(result.getAssessor());
            }
            List<FeedbackDTO> feedbackDTOs = null;
            if (result.getFeedbacks() != null && Hibernate.isInitialized(result.getFeedbacks())) {
                feedbackDTOs = result.getFeedbacks().stream().filter(Objects::nonNull).map(FeedbackDTO::of).toList();
            }
            String exerciseTitle = null;
            if (result.getSubmission() != null && Hibernate.isInitialized(result.getSubmission()) && result.getSubmission().getParticipation() != null
                    && Hibernate.isInitialized(result.getSubmission().getParticipation()) && result.getSubmission().getParticipation().getExercise() != null
                    && Hibernate.isInitialized(result.getSubmission().getParticipation().getExercise())) {

                exerciseTitle = result.getSubmission().getParticipation().getExercise().getTitle();
            }
            return new ResultSimpleDTO(result.getId(), result.getCompletionDate(), result.getScore(), result.isRated(), result.getAssessmentType(),
                    result.getSubmission() != null ? SubmissionWithParticipationDTO.of(result.getSubmission()) : null, assessor, feedbackDTOs, exerciseTitle);
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

        ResultSimpleDTO resultDTO = ResultSimpleDTO.of(complaint.getResult());
        ComplaintResponseDTO complaintResponseDTO = complaint.getComplaintResponse() != null ? ComplaintResponseDTO.of(complaint.getComplaintResponse()) : null;

        ParticipantDTO participantDTO = null;
        if (complaint.getParticipant() != null) {
            boolean isStudent = complaint.getParticipant() instanceof User;
            participantDTO = new ParticipantDTO(complaint.getParticipant().getId(), complaint.getParticipant().getName(), complaint.getParticipant().getParticipantIdentifier(),
                    isStudent);
        }

        return new ComplaintDTO(complaint.getId(), complaint.getComplaintText(), complaint.getSubmittedTime(), complaint.getComplaintType(), complaint.isAccepted(),
                complaintResponseDTO, resultDTO, participantDTO);
    }
}
