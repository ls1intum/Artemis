package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;

/**
 * DTO used by the participation management view to display paginated participations.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationManagementDTO(long participationId, InitializationState initializationState, ZonedDateTime initializationDate, int submissionCount,
        String participantName, String participantIdentifier, Long studentId, String studentLogin, Long teamId, List<UserNameAndLoginDTO> teamStudents, boolean testRun,
        Double presentationScore, ZonedDateTime individualDueDate, String buildPlanId, String repositoryUri, Boolean buildFailed, Boolean lastResultIsManual) {

    /**
     * Removes participant information, including repository identifiers that contain the student login or team name.
     *
     * @return an anonymous participation response
     */
    public ParticipationManagementDTO withoutParticipantInformation() {
        return new ParticipationManagementDTO(participationId, initializationState, initializationDate, submissionCount, null, null, null, null, null, null, testRun,
                presentationScore, individualDueDate, null, null, buildFailed, lastResultIsManual);
    }

}
