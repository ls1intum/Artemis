package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.core.dto.UserWithIdAndLoginDTO;

/**
 * DTO for a complaint response.
 *
 * <p>
 * This DTO is used to transfer complaint response data to the client
 * without exposing the full {@link ComplaintResponse} entity.
 * </p>
 *
 * @param id                  the unique identifier of the complaint response
 * @param responseText        the response text written by the reviewer (can be {@code null} for lock responses)
 * @param submittedTime       the time when the response was submitted (can be {@code null} for lock responses)
 * @param isCurrentlyLocked   whether the complaint response is currently locked (true if the lock is still active, false otherwise)
 * @param lockEndDate         the time when the lock will end (can be {@code null} if the lock has already ended or if the response is not a lock response)
 * @param complaintIsAccepted whether the complaint was accepted
 * @param complaintId         the ID of the associated complaint
 * @param reviewer            the name and login of the reviewer who submitted the response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComplaintResponseDTO(@NotNull Long id, String responseText, ZonedDateTime submittedTime, Boolean isCurrentlyLocked, ZonedDateTime lockEndDate,
        Boolean complaintIsAccepted, @NotNull Long complaintId, UserWithIdAndLoginDTO reviewer) {

    /**
     * Creates a {@link ComplaintResponseDTO} from a {@link ComplaintResponse} entity.
     *
     * @param entity the complaint response entity to convert
     * @return the corresponding DTO
     * @throws NullPointerException if required fields are missing
     */
    public static ComplaintResponseDTO of(ComplaintResponse entity) {
        Objects.requireNonNull(entity, "The complaint response must be set");
        Objects.requireNonNull(entity.getComplaint(), "The associated complaint must exist");

        UserWithIdAndLoginDTO reviewerDTO = null;
        if (entity.getReviewer() != null && entity.getReviewer().getLogin() != null) {
            reviewerDTO = new UserWithIdAndLoginDTO(entity.getReviewer().getId(), entity.getReviewer().getLogin());
        }

        return new ComplaintResponseDTO(entity.getId(), entity.getResponseText(), entity.getSubmittedTime(), entity.isCurrentlyLocked(), entity.lockEndDate(),
                entity.getComplaint().isAccepted(), entity.getComplaint().getId(), reviewerDTO);
    }
}
