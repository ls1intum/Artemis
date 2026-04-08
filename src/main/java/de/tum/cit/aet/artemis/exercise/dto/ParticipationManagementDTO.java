package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;

/**
 * DTO used by the participation management view to display paginated participations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipationManagementDTO(long participationId, InitializationState initializationState, ZonedDateTime initializationDate, int submissionCount,
        String participantName, String participantIdentifier, Long studentId, String studentLogin, Long teamId, List<UserNameAndLoginDTO> teamStudents, boolean testRun,
        Double presentationScore, ZonedDateTime individualDueDate, String buildPlanId, String repositoryUri, Boolean buildFailed, Boolean lastResultIsManual) {
}
