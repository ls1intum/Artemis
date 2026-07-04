package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;

/**
 * DTO representing a result nested in a submission response.
 *
 * @param id                  the result identifier
 * @param completionDate      the completion date, if the assessment is complete
 * @param successful          whether the result is successful, if known
 * @param score               the score in percent, if available
 * @param rated               whether the result counts toward the exercise score
 * @param assessmentType      the assessment type, if available
 * @param hasComplaint        whether a complaint exists, if known
 * @param exampleResult       whether this is an example result, if known
 * @param testCaseCount       the number of programming test cases, if available
 * @param passedTestCaseCount the number of passed programming test cases, if available
 * @param codeIssueCount      the number of programming code issues, if available
 * @param assessor            safe public assessor information, if initialized
 * @param feedbacks           initialized feedback, when requested by the enclosing response
 * @param assessmentNote      the initialized internal assessment note, if available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionResultDTO(Long id, @Nullable ZonedDateTime completionDate, @Nullable Boolean successful, @Nullable Double score, boolean rated,
        @Nullable AssessmentType assessmentType, @Nullable Boolean hasComplaint, @Nullable Boolean exampleResult, @Nullable Integer testCaseCount,
        @Nullable Integer passedTestCaseCount, @Nullable Integer codeIssueCount, @Nullable UserPublicInfoDTO assessor, @Nullable List<SubmissionFeedbackDTO> feedbacks,
        @Nullable SubmissionAssessmentNoteDTO assessmentNote) implements Serializable {

    /**
     * Maps a result including initialized feedback.
     *
     * @param result the result to map
     * @return the submission result DTO
     */
    public static SubmissionResultDTO of(Result result) {
        return of(result, true);
    }

    /**
     * Maps a result without initializing lazy associations.
     *
     * @param result          the result to map
     * @param includeFeedback whether initialized feedback should be included
     * @return the submission result DTO
     */
    public static SubmissionResultDTO of(Result result, boolean includeFeedback) {
        Objects.requireNonNull(result, "The result must be set");

        UserPublicInfoDTO assessor = result.getAssessor() != null && Hibernate.isInitialized(result.getAssessor()) ? new UserPublicInfoDTO(result.getAssessor()) : null;
        List<SubmissionFeedbackDTO> feedbacks = includeFeedback && Hibernate.isInitialized(result.getFeedbacks())
                ? result.getFeedbacks().stream().filter(Objects::nonNull).map(SubmissionFeedbackDTO::of).toList()
                : null;
        AssessmentNote assessmentNote = result.getAssessmentNote();

        return new SubmissionResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), result.getAssessmentType(),
                result.hasComplaint(), result.isExampleResult(), result.getTestCaseCount(), result.getPassedTestCaseCount(), result.getCodeIssueCount(), assessor, feedbacks,
                assessmentNote != null ? SubmissionAssessmentNoteDTO.of(assessmentNote) : null);
    }
}
