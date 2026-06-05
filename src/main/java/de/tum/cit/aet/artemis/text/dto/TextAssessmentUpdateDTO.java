package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;

/**
 * Input DTO for updating a text assessment after a complaint.
 * <p>
 * This DTO intentionally does NOT implement {@code AssessmentUpdateBaseDTO}: that interface requires entity-typed
 * {@code List<Feedback> feedbacks()} and {@code ComplaintResponse complaintResponse()}, which are incompatible with the dumb
 * DTO component types ({@link FeedbackDTO} / {@link ComplaintResponseRequestDTO}). The controller adapts these DTOs to the
 * entity types before delegating to the shared assessment-update logic. The {@link ComplaintResponseRequestDTO} mirrors the
 * {@code ComplaintResponse} wire shape the client sends (a nested complaint carrying the accept/reject decision).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextAssessmentUpdateDTO(List<FeedbackDTO> feedbacks, ComplaintResponseRequestDTO complaintResponse, String assessmentNote, Set<TextBlockDTO> textBlocks)
        implements Serializable {
}
