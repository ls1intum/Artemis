package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;

/**
 * DTO used by the participation management view to display paginated participations together with the newest result of their latest submission.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationManagementDTO(long participationId, InitializationState initializationState, ZonedDateTime initializationDate, int submissionCount,
        String participantName, String participantIdentifier, Long studentId, String studentLogin, Long teamId, List<UserNameAndLoginDTO> teamStudents, boolean testRun,
        Double presentationScore, ZonedDateTime individualDueDate, String buildPlanId, String repositoryUri, Boolean buildFailed, Boolean lastResultIsManual, Long submissionId,
        ZonedDateTime submissionDate, Long resultId, Double score, Boolean successful, ZonedDateTime completionDate, AssessmentType assessmentType, String assessmentNote,
        Long durationInSeconds, Integer testCaseCount, Integer passedTestCaseCount, Integer codeIssueCount, List<CorrectionRoundResultDTO> correctionRoundResults,
        List<Integer> assessableCorrectionRounds, Language submissionLanguage) {

    /**
     * Removes participant information, including repository identifiers that contain the student login or team name.
     *
     * @return an anonymous participation response
     */
    public ParticipationManagementDTO withoutParticipantInformation() {
        return new ParticipationManagementDTO(participationId, initializationState, initializationDate, submissionCount, null, null, null, null, null, null, testRun,
                presentationScore, individualDueDate, null, null, buildFailed, lastResultIsManual, submissionId, submissionDate, resultId, score, successful, completionDate,
                assessmentType, assessmentNote, durationInSeconds, testCaseCount, passedTestCaseCount, codeIssueCount, correctionRoundResults, assessableCorrectionRounds,
                submissionLanguage);
    }

    /** Hides other assessors' grades until this tutor completes the final correction. */
    public ParticipationManagementDTO forIndependentAssessment(int correctionRounds) {
        boolean finalCorrectionCompleted = correctionRounds > 1 && correctionRoundResults.stream().anyMatch(
                result -> result.ownedByCurrentUser() && result.correctionRound() != null && result.correctionRound() == correctionRounds - 1 && result.completionDate() != null);
        if (finalCorrectionCompleted) {
            return this;
        }
        boolean latestResultOwned = correctionRoundResults.stream().anyMatch(result -> result.ownedByCurrentUser() && Long.valueOf(result.resultId()).equals(resultId));
        var visibleResults = correctionRoundResults.stream()
                .map(result -> result.ownedByCurrentUser() ? result
                        : new CorrectionRoundResultDTO(result.submissionId(), result.resultId(), result.correctionRound(), result.assessmentType(), result.completionDate(),
                                result.hasComplaint(), null, false))
                .toList();
        return new ParticipationManagementDTO(participationId, initializationState, initializationDate, submissionCount, participantName, participantIdentifier, studentId,
                studentLogin, teamId, teamStudents, testRun, presentationScore, individualDueDate, buildPlanId, repositoryUri, buildFailed, lastResultIsManual, submissionId,
                submissionDate, latestResultOwned ? resultId : null, latestResultOwned ? score : null, latestResultOwned ? successful : null, completionDate, assessmentType,
                latestResultOwned ? assessmentNote : null, latestResultOwned ? durationInSeconds : null, latestResultOwned ? testCaseCount : null,
                latestResultOwned ? passedTestCaseCount : null, latestResultOwned ? codeIssueCount : null, visibleResults, assessableCorrectionRounds, submissionLanguage);
    }

}
