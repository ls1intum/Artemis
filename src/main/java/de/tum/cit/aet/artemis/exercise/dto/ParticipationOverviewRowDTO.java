package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * One flat row of the user's course-overview participation projection. A participation and its latest submission can
 * produce several rows when that submission has several results; the overview service groups those small rows before
 * applying the existing result-visibility rules.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationOverviewRowDTO(long exerciseId, long participationId, String participationType, @Nullable InitializationState initializationState,
        @Nullable ZonedDateTime initializationDate, @Nullable Boolean testRun, @Nullable ZonedDateTime individualDueDate, @Nullable Double presentationScore,
        @Nullable String repositoryUri, @Nullable Long submissionId, @Nullable ZonedDateTime submissionDate, @Nullable Boolean submitted, @Nullable SubmissionType submissionType,
        @Nullable Boolean submissionBuildFailed, @Nullable Long resultId, @Nullable ZonedDateTime resultCompletionDate, @Nullable Double resultScore, @Nullable Boolean resultRated,
        @Nullable Boolean resultSuccessful, @Nullable AssessmentType resultAssessmentType, @Nullable Integer resultCodeIssueCount, @Nullable Integer resultTestCaseCount,
        @Nullable Integer resultPassedTestCaseCount) {

    /**
     * JPQL constructor accepting the entity class produced by Hibernate's {@code TYPE(...)} function.
     */
    public ParticipationOverviewRowDTO(long exerciseId, long participationId, Class<? extends Participation> participationType, @Nullable InitializationState initializationState,
            @Nullable ZonedDateTime initializationDate, @Nullable Boolean testRun, @Nullable ZonedDateTime individualDueDate, @Nullable Double presentationScore,
            @Nullable String repositoryUri, @Nullable Long submissionId, @Nullable ZonedDateTime submissionDate, @Nullable Boolean submitted,
            @Nullable SubmissionType submissionType, @Nullable Boolean submissionBuildFailed, @Nullable Long resultId, @Nullable ZonedDateTime resultCompletionDate,
            @Nullable Double resultScore, @Nullable Boolean resultRated, @Nullable Boolean resultSuccessful, @Nullable AssessmentType resultAssessmentType,
            @Nullable Integer resultCodeIssueCount, @Nullable Integer resultTestCaseCount, @Nullable Integer resultPassedTestCaseCount) {
        this(exerciseId, participationId, participationTypeName(participationType), initializationState, initializationDate, testRun, individualDueDate, presentationScore,
                repositoryUri, submissionId, submissionDate, submitted, submissionType, submissionBuildFailed, resultId, resultCompletionDate, resultScore, resultRated,
                resultSuccessful, resultAssessmentType, resultCodeIssueCount, resultTestCaseCount, resultPassedTestCaseCount);
    }

    public boolean isTestRun() {
        return Boolean.TRUE.equals(testRun);
    }

    public @Nullable ResultOverviewDTO toResultOverviewDTO() {
        return resultId == null ? null
                : new ResultOverviewDTO(resultId, resultCompletionDate, resultScore, resultRated, resultSuccessful, resultAssessmentType, resultCodeIssueCount, resultTestCaseCount,
                        resultPassedTestCaseCount);
    }

    /**
     * Projects the row's submission for the overview.
     * <p>
     * The client discriminates submissions on {@code submissionExerciseType}, which is taken from the exercise rather
     * than read back from the submission row. Selecting {@code TYPE(submission)} would be the direct way, but the
     * submission is reached through an outer join: a participation that was started and never submitted has no
     * submission row, and Hibernate fails to map that null discriminator to an entity ("Could not resolve discriminator
     * value") while reading the result set — before this record is ever constructed. A submission always belongs to an
     * exercise of the matching kind, so the exercise type carries the same information without the outer-join hazard.
     *
     * @param results      the results to attach to the submission
     * @param exerciseType the type of the exercise the participation belongs to
     * @return the projected submission, or null if the row carries no submission
     */
    public @Nullable SubmissionOverviewDTO toSubmissionOverviewDTO(List<ResultOverviewDTO> results, ExerciseType exerciseType) {
        return submissionId == null ? null
                : new SubmissionOverviewDTO(submissionId, submissionDate, Boolean.TRUE.equals(submitted), submissionType, exerciseType.getValue(), submissionBuildFailed, results);
    }

    private static String participationTypeName(Class<? extends Participation> participationType) {
        return participationType == ProgrammingExerciseStudentParticipation.class ? "programming" : "student";
    }
}
