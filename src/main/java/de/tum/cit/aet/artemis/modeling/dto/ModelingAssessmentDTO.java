package de.tum.cit.aet.artemis.modeling.dto;

import java.util.List;

import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;

/**
 * Input DTO for saving or submitting a modeling assessment.
 * <p>
 * The controller maps {@link FeedbackDTO} to {@code Feedback} before persisting. No {@code @JsonInclude(NON_EMPTY)} here on
 * purpose: this is a request body and an empty {@code feedbacks} list must stay an empty array on the wire (the save path
 * clears the existing feedback when an empty list is sent, mirroring the previous behavior where the list was never null).
 *
 * @param feedbacks      the feedback items of the assessment
 * @param assessmentNote the optional assessment note attached to the result
 */
public record ModelingAssessmentDTO(List<FeedbackDTO> feedbacks, String assessmentNote) {
}
