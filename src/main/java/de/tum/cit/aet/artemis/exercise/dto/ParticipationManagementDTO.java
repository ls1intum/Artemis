package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;

/**
 * DTO used by the participation management view to display paginated participations together with the newest result of their latest submission.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationManagementDTO(long participationId, InitializationState initializationState, ZonedDateTime initializationDate, int submissionCount,
        String participantName, String participantIdentifier, Long studentId, String studentLogin, Long teamId, List<UserNameAndLoginDTO> teamStudents, boolean testRun,
        Double presentationScore, ZonedDateTime individualDueDate, String buildPlanId, String repositoryUri, Boolean buildFailed, Boolean lastResultIsManual, Long submissionId,
        Long resultId, Double score, Boolean successful, ZonedDateTime completionDate, AssessmentType assessmentType, String assessmentNote, Long durationInSeconds,
        Integer testCaseCount, Integer passedTestCaseCount, Integer codeIssueCount, List<CorrectionRoundResultDTO> correctionRoundResults) {

    /**
     * Removes participant information, including repository identifiers that contain the student login or team name.
     *
     * @return an anonymous participation response
     */
    public ParticipationManagementDTO withoutParticipantInformation() {
        return new ParticipationManagementDTO(participationId, initializationState, initializationDate, submissionCount, null, null, null, null, null, null, testRun,
                presentationScore, individualDueDate, null, null, buildFailed, lastResultIsManual, submissionId, resultId, score, successful, completionDate, assessmentType,
                assessmentNote, durationInSeconds, testCaseCount, passedTestCaseCount, codeIssueCount, correctionRoundResults);
    }

}
