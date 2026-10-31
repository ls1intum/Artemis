package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.core.domain.Language;

/** One submission and its available and existing assessment actions in the assessment queue. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentQueueRowDTO(long participationId, int submissionCount, @Nullable String participantName, @Nullable String participantIdentifier, @Nullable Long teamId,
        @Nullable List<UserNameAndLoginDTO> teamStudents, boolean testRun, @Nullable ZonedDateTime individualDueDate, @Nullable Boolean buildFailed, @Nullable Long submissionId,
        @Nullable ZonedDateTime submissionDate, @Nullable Long resultId, @Nullable Double score, @Nullable Boolean successful, @Nullable ZonedDateTime completionDate,
        @Nullable AssessmentType assessmentType, @Nullable Integer testCaseCount, @Nullable Integer passedTestCaseCount, @Nullable Integer codeIssueCount,
        List<CorrectionRoundResultDTO> correctionRoundResults, List<Integer> assessableCorrectionRounds, @Nullable Language submissionLanguage,
        List<AssessmentDraftDTO> olderAssessmentDrafts) {

    public AssessmentQueueRowDTO withoutParticipantInformation() {
        return new AssessmentQueueRowDTO(participationId, submissionCount, null, null, null, null, testRun, individualDueDate, buildFailed, submissionId, submissionDate, resultId,
                score, successful, completionDate, assessmentType, testCaseCount, passedTestCaseCount, codeIssueCount, correctionRoundResults, assessableCorrectionRounds,
                submissionLanguage, olderAssessmentDrafts);
    }
}
