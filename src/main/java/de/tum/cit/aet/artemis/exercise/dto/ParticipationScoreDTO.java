package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;

/**
 * DTO used by the exercise scores view to display paginated participation results.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationScoreDTO(long participationId, ZonedDateTime initializationDate, int submissionCount, String participantName, String participantIdentifier,
        Long studentId, Long teamId, Long resultId, Double score, Boolean successful, ZonedDateTime completionDate, AssessmentType assessmentType, String assessmentNote,
        Long durationInSeconds, Long submissionId, Boolean buildFailed, String buildPlanId, String repositoryUri, boolean testRun, Integer testCaseCount,
        Integer passedTestCaseCount, Integer codeIssueCount, List<CorrectionRoundResultDTO> correctionRoundResults) {

    /**
     * Removes participant information, including repository identifiers that contain the student login or team name.
     *
     * @return an anonymous participation response
     */
    public ParticipationScoreDTO withoutParticipantInformation() {
        return new ParticipationScoreDTO(participationId, initializationDate, submissionCount, null, null, null, null, resultId, score, successful, completionDate, assessmentType,
                assessmentNote, durationInSeconds, submissionId, buildFailed, null, null, testRun, testCaseCount, passedTestCaseCount, codeIssueCount, correctionRoundResults);
    }

}
