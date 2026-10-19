package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;

/**
 * Input DTO for submitting a text assessment.
 * <p>
 * The controller maps {@link FeedbackDTO} to {@code Feedback} and {@link TextBlockDTO} to {@code TextBlock} before persisting.
 * No {@code @JsonInclude(NON_EMPTY)} here on purpose: this is a request body and an empty {@code feedbacks} list must stay an
 * empty array on the wire (the submit path streams it unguarded, mirroring the previous behavior where the list was never null).
 */
public record TextAssessmentDTO(List<FeedbackDTO> feedbacks, Set<TextBlockDTO> textBlocks, String assessmentNote) implements Serializable {
}
