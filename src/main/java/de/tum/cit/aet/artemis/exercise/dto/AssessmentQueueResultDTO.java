package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;

/** Scalar result metadata for the team queue, without loading submissions or feedback. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentQueueResultDTO(Long submissionId, Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, AssessmentType assessmentType,
        Integer correctionRound, Boolean hasComplaint, Boolean exampleResult, Integer testCaseCount, Integer passedTestCaseCount, Integer codeIssueCount, Long assessorId,
        String assessorLogin, String assessorFirstName, String assessorLastName) {

    /** Builds the existing queue result response, retaining the assessor's display-name fallback. */
    public SubmissionResponseDTO.SubmissionResultDTO toResponse() {
        var assessor = assessorId == null ? null : new UserNameDTO(assessorId, assessorLogin, User.displayName(assessorFirstName, assessorLastName, assessorLogin));
        return new SubmissionResponseDTO.SubmissionResultDTO(id, completionDate, successful, score, rated, assessmentType, correctionRound, hasComplaint, exampleResult,
                testCaseCount, passedTestCaseCount, codeIssueCount, assessor, null);
    }
}
