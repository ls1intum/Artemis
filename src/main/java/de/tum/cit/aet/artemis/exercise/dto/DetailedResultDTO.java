package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;

/**
 * A result nested in a submission of the exercise details or the participation submissions.
 * <p>
 * It repeats every scalar the {@code Result} entity serialized there. Neither route fetches the feedbacks or the
 * assessment note, so the entity left both off the wire and this record does not carry them. The assessor is present
 * where the route fetched it and did not filter it out.
 *
 * @param id                  the id of the result
 * @param completionDate      when the assessment was completed
 * @param successful          whether the result is successful
 * @param score               the score in percent
 * @param rated               whether the result is rated
 * @param assessor            the assessor, where loaded and not filtered out
 * @param assessmentType      the assessment type
 * @param correctionRound     the correction round
 * @param hasComplaint        whether a complaint was filed
 * @param exampleResult       whether the result belongs to an example submission
 * @param testCaseCount       the number of test cases, for programming exercises
 * @param passedTestCaseCount the number of passed test cases, for programming exercises
 * @param codeIssueCount      the number of code issues, for programming exercises
 * @param exerciseId          the id of the exercise the result belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DetailedResultDTO(Long id, @Nullable ZonedDateTime completionDate, @Nullable Boolean successful, @Nullable Double score, boolean rated,
        @Nullable ParticipantUserDTO assessor, @Nullable AssessmentType assessmentType, @Nullable Integer correctionRound, @Nullable Boolean hasComplaint,
        @Nullable Boolean exampleResult, @Nullable Integer testCaseCount, @Nullable Integer passedTestCaseCount, @Nullable Integer codeIssueCount, long exerciseId) {

    /**
     * Maps an already filtered result.
     *
     * @param result the result to map
     * @return the result as the submission payloads carry it
     */
    public static DetailedResultDTO of(Result result) {
        return new DetailedResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(),
                ParticipantUserDTO.of(result.getAssessor()), result.getAssessmentType(), result.getCorrectionRound(), result.getHasComplaint().orElse(null),
                result.isExampleResult(), result.getTestCaseCount(), result.getPassedTestCaseCount(), result.getCodeIssueCount(), result.getExerciseId());
    }
}
