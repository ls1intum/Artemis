package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;

/**
 * Lean result information nested in a participation submission response.
 *
 * @param id                  the unique identifier of the result
 * @param completionDate      the time at which the result was completed, if available
 * @param successful          whether the result was successful, if determined
 * @param score               the achieved score in percent, if calculated
 * @param rated               whether the result contributes to the exercise score
 * @param assessmentType      the type of assessment that produced the result, if known
 * @param testCaseCount       the number of executed test cases, if available
 * @param passedTestCaseCount the number of passed test cases, if available
 * @param codeIssueCount      the number of static code analysis issues, if available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationSubmissionResultDTO(Long id, @Nullable ZonedDateTime completionDate, @Nullable Boolean successful, @Nullable Double score, boolean rated,
        @Nullable AssessmentType assessmentType, @Nullable Integer testCaseCount, @Nullable Integer passedTestCaseCount, @Nullable Integer codeIssueCount) {

    /**
     * Creates a lean response DTO from a result.
     *
     * @param result the result to map
     * @return the lean result response
     */
    public static ParticipationSubmissionResultDTO of(Result result) {
        return new ParticipationSubmissionResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(),
                result.getAssessmentType(), result.getTestCaseCount(), result.getPassedTestCaseCount(), result.getCodeIssueCount());
    }
}
