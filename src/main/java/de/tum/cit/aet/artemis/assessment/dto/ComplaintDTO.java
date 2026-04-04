package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;

/**
 * DTO for a complaint.
 *
 * <p>
 * This DTO is used to transfer complaint data to the client
 * without exposing the full {@link de.tum.cit.aet.artemis.assessment.domain.Complaint} entity.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ComplaintDTO(@NotNull Long id, String complaintText, ZonedDateTime submittedTime, ComplaintType complaintType, Boolean complaintIsAccepted,
        ComplaintResponseDTO complaintResponse, @NotNull ResultDTO result, Long participantId) {

    /**
     * Creates a {@link ComplaintDTO} from a {@link Complaint} entity.
     *
     * @param complaint the complaint entity to convert
     * @return the corresponding DTO
     * @throws NullPointerException if required fields are missing
     */
    public static ComplaintDTO of(Complaint complaint) {
        Objects.requireNonNull(complaint, "The complaint must be set");
        Objects.requireNonNull(complaint.getResult(), "The associated result must exist");

        ResultDTO resultDTO = ResultDTO.of(complaint.getResult());
        Long participantId = complaint.getParticipant() != null ? complaint.getParticipant().getId() : null;
        ComplaintResponseDTO complaintResponseDTO = complaint.getComplaintResponse() != null ? ComplaintResponseDTO.of(complaint.getComplaintResponse()) : null;

        return new ComplaintDTO(complaint.getId(), complaint.getComplaintText(), complaint.getSubmittedTime(), complaint.getComplaintType(), complaint.isAccepted(),
                complaintResponseDTO, resultDTO, participantId);
    }
}
