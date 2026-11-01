package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;

/**
 * DTO used by the participation management view to display paginated participations together with the newest result of their latest submission.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationManagementDTO(@JsonProperty(required = true) long participationId, @Nullable InitializationState initializationState,
        @Nullable ZonedDateTime initializationDate, @JsonProperty(required = true) int submissionCount, @Nullable String participantName, @Nullable String participantIdentifier,
        @Nullable Long studentId, @Nullable String studentLogin, @Nullable Long teamId, @Nullable List<UserNameAndLoginDTO> teamStudents,
        @JsonProperty(required = true) boolean testRun, @Nullable Double presentationScore, @Nullable ZonedDateTime individualDueDate, @Nullable String buildPlanId,
        @Nullable String repositoryUri, @Nullable Boolean buildFailed, @Nullable Boolean lastResultIsManual, @Nullable Long submissionId, @Nullable ZonedDateTime submissionDate,
        @Nullable Long resultId, @Nullable Double score, @Nullable Boolean successful, @Nullable ZonedDateTime completionDate, @Nullable AssessmentType assessmentType,
        @Nullable String assessmentNote, @Nullable Long durationInSeconds, @Nullable Integer testCaseCount, @Nullable Integer passedTestCaseCount, @Nullable Integer codeIssueCount,
        @NonNull List<CorrectionRoundResultDTO> correctionRoundResults) {

    /**
     * Removes participant information, including repository identifiers that contain the student login or team name.
     *
     * @return an anonymous participation response
     */
    public ParticipationManagementDTO withoutParticipantInformation() {
        return new ParticipationManagementDTO(participationId, initializationState, initializationDate, submissionCount, null, null, null, null, null, null, testRun,
                presentationScore, individualDueDate, null, null, buildFailed, lastResultIsManual, submissionId, submissionDate, resultId, score, successful, completionDate,
                assessmentType, assessmentNote, durationInSeconds, testCaseCount, passedTestCaseCount, codeIssueCount, correctionRoundResults);
    }

}
