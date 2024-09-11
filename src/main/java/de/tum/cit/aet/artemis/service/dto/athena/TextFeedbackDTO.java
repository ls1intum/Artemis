package de.tum.cit.aet.artemis.service.dto.athena;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.text.domain.TextBlock;

/**
 * A DTO representing a Feedback on a TextExercise, for transferring data to Athena and receiving suggestions from Athena
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextFeedbackDTO(long id, long exerciseId, long submissionId, String title, String description, double credits, Long structuredGradingInstructionId,
        Integer indexStart, Integer indexEnd) implements FeedbackBaseDTO {

    /**
     * Creates a TextFeedbackDTO from a Feedback object
     *
     * @param exerciseId    the id of the exercise the feedback is given for
     * @param submissionId  the id of the submission the feedback is given for
     * @param feedback      the feedback object
     * @param feedbackBlock the TextBlock that the feedback is on (must be passed because this record cannot fetch it for itself)
     * @return the TextFeedbackDTO
     */
    public static TextFeedbackDTO of(long exerciseId, long submissionId, @NotNull de.tum.cit.aet.artemis.domain.Feedback feedback, TextBlock feedbackBlock) {
        Integer startIndex = feedbackBlock == null ? null : feedbackBlock.getStartIndex();
        Integer endIndex = feedbackBlock == null ? null : feedbackBlock.getEndIndex();
        Long gradingInstructionId = null;
        if (feedback.getGradingInstruction() != null) {
            gradingInstructionId = feedback.getGradingInstruction().getId();
        }
        return new TextFeedbackDTO(feedback.getId(), exerciseId, submissionId, feedback.getText(), feedback.getDetailText(), feedback.getCredits(), gradingInstructionId,
                startIndex, endIndex);
    }
}
