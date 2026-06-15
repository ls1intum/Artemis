package de.tum.cit.aet.artemis.fileupload.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateDTO;

/**
 * DTO for updating a file upload assessment after an accepted complaint.
 *
 * @param feedbacks         the updated feedback items
 * @param complaintResponse the complaint response resolving the complaint
 * @param assessmentNote    the optional private assessment note
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadAssessmentUpdateDTO(List<@Valid FileUploadFeedbackInputDTO> feedbacks, @NotNull @Valid FileUploadComplaintResponseInputDTO complaintResponse,
        String assessmentNote) {

    /**
     * Creates detached feedback entity state for the existing assessment service.
     *
     * @return the detached feedback entities, or {@code null} if no feedback list was provided
     */
    public List<Feedback> feedbackEntities() {
        return feedbacks != null ? feedbacks.stream().map(FileUploadFeedbackInputDTO::toEntity).toList() : null;
    }

    /**
     * Adapts this REST input to the existing internal assessment update contract.
     *
     * @return the internal assessment update
     */
    public AssessmentUpdateDTO toAssessmentUpdateDTO() {
        return new AssessmentUpdateDTO(feedbackEntities(), complaintResponse != null ? complaintResponse.toEntity() : null, assessmentNote);
    }
}
