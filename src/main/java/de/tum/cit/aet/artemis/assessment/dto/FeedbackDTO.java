package de.tum.cit.aet.artemis.assessment.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;

/**
 * DTO containing {@link Feedback} information.
 * This does not include large reference attributes (e.g. long feedback text) in order to send minimal data to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDTO(Long id, String text, String detailText, boolean hasLongFeedbackText, String reference, Double credits, Boolean positive, FeedbackType type,
        Visibility visibility, GradingInstructionDTO gradingInstruction) implements Serializable {

    /**
     * Converts a Feedback into a FeedbackDTO.
     *
     * @param feedback to convert
     * @return the converted DTO, or null if the feedback is null
     */
    public static FeedbackDTO of(Feedback feedback) {
        if (feedback == null) {
            return null;
        }
        GradingInstructionDTO gradingInstruction = feedback.getGradingInstruction() != null ? GradingInstructionDTO.of(feedback.getGradingInstruction()) : null;
        return new FeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(), feedback.getCredits(),
                feedback.isPositive(), feedback.getType(), feedback.getVisibility(), gradingInstruction);
    }
}
