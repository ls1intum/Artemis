package de.tum.cit.aet.artemis.assessment.service;

import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateBaseDTO;

/**
 * Entity-shaped carrier the resources build from their request DTOs before delegating to
 * {@link AssessmentService#updateAssessmentAfterComplaint}. Internal only: it holds entities, so it is neither a REST body
 * nor a DTO.
 *
 * @param feedbacks         the updated feedback list
 * @param complaintResponse the corresponding complaint response
 * @param assessmentNote    the assessment note
 */
public record AssessmentUpdate(List<Feedback> feedbacks, ComplaintResponse complaintResponse, @Nullable String assessmentNote) implements AssessmentUpdateBaseDTO {
}
