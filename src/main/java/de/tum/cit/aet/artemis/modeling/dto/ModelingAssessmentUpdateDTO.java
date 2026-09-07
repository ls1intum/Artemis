package de.tum.cit.aet.artemis.modeling.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;

/**
 * Input DTO for updating a modeling assessment after a complaint.
 * <p>
 * This DTO intentionally does NOT implement {@code AssessmentUpdateBaseDTO}: that interface requires entity-typed
 * {@code List<Feedback> feedbacks()} and {@code ComplaintResponse complaintResponse()}, which are incompatible with the dumb
 * DTO component types ({@link FeedbackDTO} / {@link ComplaintResponseRequestDTO}). The controller adapts these DTOs to the
 * entity types before delegating to the shared assessment-update logic. The {@link ComplaintResponseRequestDTO} mirrors the
 * {@code ComplaintResponse} wire shape the client sends (a nested complaint carrying the accept/reject decision).
 * Bare {@code @JsonInclude} (no explicit value, i.e. Jackson's ALWAYS default) is used rather than {@code NON_EMPTY}: this is a request
 * body and an empty {@code feedbacks} list must stay an empty array on the wire.
 *
 * @param feedbacks         the updated feedback items of the assessment
 * @param complaintResponse the response to the complaint carrying the resolution decision
 * @param assessmentNote    the optional assessment note attached to the result
 */
@JsonInclude
public record ModelingAssessmentUpdateDTO(List<FeedbackDTO> feedbacks, ComplaintResponseRequestDTO complaintResponse, String assessmentNote) {
}
