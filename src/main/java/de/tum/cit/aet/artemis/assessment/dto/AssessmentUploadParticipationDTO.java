package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal participation data required to validate an uploaded manual assessment identifier.
 * <p>
 * <b>Preconditions:</b> {@code participationId} identifies a persisted participation and {@code participantIdentifier} is non-blank.
 * <p>
 * <b>Postcondition:</b> both components contain valid minimal participation data for assessment-upload validation.
 *
 * @param participationId       the participation id from the upload identifier
 * @param participantIdentifier the login or team short name belonging to the participation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentUploadParticipationDTO(long participationId, String participantIdentifier) {

    /**
     * @throws IllegalArgumentException if a precondition is violated
     */
    public AssessmentUploadParticipationDTO {
        if (participationId <= 0) {
            throw new IllegalArgumentException("The participation id must identify a persisted participation");
        }
        if (participantIdentifier == null || participantIdentifier.isBlank()) {
            throw new IllegalArgumentException("The participant identifier must not be null or blank");
        }
    }
}
