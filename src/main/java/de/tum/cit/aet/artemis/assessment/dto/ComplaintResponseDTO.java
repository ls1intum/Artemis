package de.tum.cit.aet.artemis.assessment.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.COMPLAINT_RESPONSE_TEXT_LIMIT;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ComplaintResponseDTO(long id, @Nullable @Size(max = COMPLAINT_RESPONSE_TEXT_LIMIT) String responseText, @Nullable ZonedDateTime submittedTime,
        @Nullable StudentDTO reviewer, @JsonProperty("isCurrentlyLocked") boolean isCurrentlyLocked, @JsonProperty("lockEndDate") @Nullable ZonedDateTime lockEndDate) {

    public static ComplaintResponseDTO of(ComplaintResponse complaintResponse) {
        if (complaintResponse == null) {
            return null;
        }

        StudentDTO reviewerDTO = complaintResponse.getReviewer() != null ? new StudentDTO(complaintResponse.getReviewer()) : null;

        return new ComplaintResponseDTO(complaintResponse.getId(), complaintResponse.getResponseText(), complaintResponse.getSubmittedTime(), reviewerDTO,
                complaintResponse.isCurrentlyLocked(), complaintResponse.lockEndDate());
    }

    /**
     * Creates a new ComplaintResponseDTO with sensitive information filtered out
     */
    public ComplaintResponseDTO withSensitiveInformationFiltered() {
        return new ComplaintResponseDTO(this.id, this.responseText, this.submittedTime, null, // Remove reviewer information
                this.isCurrentlyLocked, this.lockEndDate);
    }
}
