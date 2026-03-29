package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;

/**
 * DTO used by the participation management view to display paginated participations.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationManagementDTO(long participationId, InitializationState initializationState, ZonedDateTime initializationDate, int submissionCount,
        String participantName, String participantIdentifier, Long studentId, String studentLogin, Long teamId, boolean testRun, Double presentationScore,
        ZonedDateTime individualDueDate, String buildPlanId, String repositoryUri, Boolean buildFailed, Boolean lastResultIsManual) {
}
