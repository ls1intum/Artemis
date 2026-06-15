package de.tum.cit.aet.artemis.fileupload.dto;

import java.util.List;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;

/**
 * DTO for saving or submitting a file upload assessment.
 *
 * @param feedbacks      the feedback items of the assessment
 * @param assessmentNote the optional private assessment note
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadAssessmentInputDTO(List<@Valid FileUploadFeedbackInputDTO> feedbacks, String assessmentNote) {

    /**
     * Creates detached feedback entity state for the existing assessment service.
     *
     * @return the detached feedback entities, or {@code null} if no feedback list was provided
     */
    public List<Feedback> feedbackEntities() {
        return feedbacks != null ? feedbacks.stream().map(FileUploadFeedbackInputDTO::toEntity).toList() : null;
    }
}
