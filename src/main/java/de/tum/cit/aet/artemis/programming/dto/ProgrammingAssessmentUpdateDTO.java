package de.tum.cit.aet.artemis.programming.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUpdate;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingManualResultRequestDTO.ProgrammingManualFeedbackDTO;

/**
 * The body of a programming assessment update after a complaint.
 * <p>
 * The tutor editor posts the full feedback list it holds (the save replaces {@code Result.feedbacks}, so the automatic
 * feedback's {@code testCase} reference must survive, see {@link ProgrammingManualFeedbackDTO}) together with the
 * complaint response it loaded, which nests the complaint carrying the accept/reject decision. Unknown properties are
 * ignored so the client can keep posting the complete complaint response object; the bare {@code @JsonInclude()} keeps an
 * explicit empty feedback list on the wire.
 *
 * @param feedbacks         the complete feedback list of the new assessment
 * @param complaintResponse the complaint response carrying the resolution decision
 * @param assessmentNote    the internal assessment note
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude()
public record ProgrammingAssessmentUpdateDTO(List<ProgrammingManualFeedbackDTO> feedbacks, ComplaintResponseDTO complaintResponse, String assessmentNote) {

    /**
     * The complaint response as the client loaded it: only the lock id, the response text and the nested decision are read.
     *
     * @param id           the id of the (locked) complaint response
     * @param responseText the tutor's response text
     * @param complaint    the complaint carrying the accept/reject decision
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude()
    public record ComplaintResponseDTO(Long id, String responseText, ComplaintDTO complaint) {
    }

    /**
     * @param id       the complaint id
     * @param accepted whether the complaint was accepted
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude()
    public record ComplaintDTO(Long id, Boolean accepted) {
    }

    /**
     * Builds the entity-shaped update the shared assessment service works on. A missing feedback list maps to an empty
     * list (the service replaces the stored feedback with it); a missing complaint response stays null so the service
     * rejects the request with its existing error.
     *
     * @return the entity-shaped assessment update
     */
    public AssessmentUpdate toAssessmentUpdate() {
        List<Feedback> feedbackEntities = new ArrayList<>();
        if (feedbacks != null) {
            feedbacks.stream().filter(Objects::nonNull).map(ProgrammingManualFeedbackDTO::toEntity).forEach(feedbackEntities::add);
        }
        ComplaintResponse complaintResponseEntity = null;
        if (complaintResponse != null) {
            complaintResponseEntity = new ComplaintResponse();
            complaintResponseEntity.setId(complaintResponse.id());
            complaintResponseEntity.setResponseText(complaintResponse.responseText());
            if (complaintResponse.complaint() != null) {
                Complaint complaint = new Complaint();
                complaint.setId(complaintResponse.complaint().id());
                complaint.setAccepted(complaintResponse.complaint().accepted());
                complaintResponseEntity.setComplaint(complaint);
            }
        }
        return new AssessmentUpdate(feedbackEntities, complaintResponseEntity, assessmentNote);
    }
}
