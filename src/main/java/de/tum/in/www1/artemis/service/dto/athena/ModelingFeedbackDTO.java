package de.tum.in.www1.artemis.service.dto.athena;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.Feedback;

/**
 * A DTO representing a Feedback on a ModelingExercise, for transferring data to Athena and receiving suggestions from Athena
 */
public record ModelingFeedbackDTO(long id, long exerciseId, long submissionId, String title, String description, double credits, Long structuredGradingInstructionId,
        List<String> elementIds) implements FeedbackDTO {

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
