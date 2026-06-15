package de.tum.cit.aet.artemis.fileupload.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.COMPLAINT_RESPONSE_TEXT_LIMIT;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;

/**
 * DTO for resolving a complaint while updating a file upload assessment.
 *
 * @param id                  the ID of the existing complaint response lock
 * @param responseText        the response text written by the reviewer
 * @param complaintIsAccepted whether the complaint is accepted
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadComplaintResponseInputDTO(@NotNull Long id, @Size(max = COMPLAINT_RESPONSE_TEXT_LIMIT) String responseText, @NotNull Boolean complaintIsAccepted) {

    /**
     * Creates the minimal detached complaint response state required by the existing assessment service.
     *
     * @return the detached complaint response entity
     */
    public ComplaintResponse toEntity() {
        Complaint complaint = new Complaint().accepted(complaintIsAccepted);
        ComplaintResponse complaintResponse = new ComplaintResponse().responseText(responseText).complaint(complaint);
        complaintResponse.setId(id);
        return complaintResponse;
    }
}
