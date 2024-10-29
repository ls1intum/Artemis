package de.tum.cit.aet.artemis.athena.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;

/**
 * A DTO representing a Feedback on a ModelingExercise, for transferring data to Athena and receiving suggestions from Athena
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingFeedbackDTO(long id, long exerciseId, long submissionId, String title, String description, double credits, Long structuredGradingInstructionId,
        List<String> elementIds) implements FeedbackBaseDTO {

    /**
     * Creates a ModelingFeedbackDTO from a Feedback object
     *
     * @param exerciseId   the id of the exercise the feedback is given for
     * @param submissionId the id of the submission the feedback is given for
     * @param feedback     the feedback object
     * @return the ModelingFeedbackDTO
     */
    public static ModelingFeedbackDTO of(long exerciseId, long submissionId, @NotNull Feedback feedback) {
        Long gradingInstructionId = null;
        if (feedback.getGradingInstruction() != null) {
            gradingInstructionId = feedback.getGradingInstruction().getId();
        }

        return new ModelingFeedbackDTO(feedback.getId(), exerciseId, submissionId, feedback.getText(), feedback.getDetailText(), feedback.getCredits(), gradingInstructionId,
                List.of(feedback.getReference()));
    }
}
