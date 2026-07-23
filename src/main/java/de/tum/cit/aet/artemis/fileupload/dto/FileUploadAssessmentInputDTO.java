package de.tum.cit.aet.artemis.fileupload.dto;

import java.util.List;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;

/**
 * DTO for saving or submitting a file upload assessment.
 *
 * @param feedbacks      the feedback items of the assessment
 * @param assessmentNote the optional private assessment note
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadAssessmentInputDTO(@Nullable List<@Valid FileUploadFeedbackInputDTO> feedbacks, @Nullable String assessmentNote) {

    /**
     * Creates detached feedback entity state for the existing assessment service.
     *
     * @return the detached feedback entities, or {@code null} if no feedback list was provided
     */
    public @Nullable List<Feedback> feedbackEntities() {
        return feedbacks != null ? feedbacks.stream().map(FileUploadFeedbackInputDTO::toEntity).toList() : null;
    }
}
