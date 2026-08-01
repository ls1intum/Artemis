package de.tum.cit.aet.artemis.assessment.dto;

/**
 * Minimal participation data required to validate an uploaded manual assessment identifier.
 *
 * @param participationId       the participation id from the upload identifier
 * @param participantIdentifier the login or team short name belonging to the participation
 */
public record AssessmentUploadParticipationDTO(long participationId, String participantIdentifier) {
}
