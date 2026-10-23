package de.tum.cit.aet.artemis.assessment.dto;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;

/** Common complaint response input without changing each assessment endpoint's JSON inclusion rules. */
public interface ComplaintResponseInputDTO {

    Long id();

    String responseText();

    ComplaintInputDTO complaint();

    interface ComplaintInputDTO {

        Long id();

        Boolean accepted();
    }

    /**
     * Rebuilds the locked response and an id-only complaint reference for assessment update handling.
     *
     * @return transient response carrying the submitted resolution
     */
    default ComplaintResponse toEntity() {
        ComplaintResponse response = new ComplaintResponse();
        response.setId(id());
        response.setResponseText(responseText());
        Complaint complaint = new Complaint();
        if (complaint() != null) {
            complaint.setId(complaint().id());
            complaint.setAccepted(complaint().accepted());
        }
        response.setComplaint(complaint);
        return response;
    }
}
