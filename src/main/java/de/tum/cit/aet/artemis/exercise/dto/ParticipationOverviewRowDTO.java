package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * One flat row of the user's course-overview participation projection. A participation and its latest submission can
 * produce several rows when that submission has several results; the overview service groups those small rows before
 * applying the existing result-visibility rules.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationOverviewRowDTO(long exerciseId, long participationId, String participationType, @Nullable InitializationState initializationState,
        @Nullable ZonedDateTime initializationDate, @Nullable Boolean testRun, @Nullable ZonedDateTime individualDueDate, @Nullable Double presentationScore,
        @Nullable String repositoryUri, @Nullable Long submissionId, @Nullable ZonedDateTime submissionDate, @Nullable Boolean submitted, @Nullable SubmissionType submissionType,
        @Nullable String submissionExerciseType, @Nullable Long resultId, @Nullable ZonedDateTime resultCompletionDate, @Nullable Double resultScore, @Nullable Boolean resultRated,
        @Nullable Boolean resultSuccessful, @Nullable AssessmentType resultAssessmentType, @Nullable Integer resultCodeIssueCount) {

    /**
     * JPQL constructor accepting the entity classes produced by Hibernate's {@code TYPE(...)} function.
     */
    public ParticipationOverviewRowDTO(long exerciseId, long participationId, Class<? extends Participation> participationType, @Nullable InitializationState initializationState,
            @Nullable ZonedDateTime initializationDate, @Nullable Boolean testRun, @Nullable ZonedDateTime individualDueDate, @Nullable Double presentationScore,
            @Nullable String repositoryUri, @Nullable Long submissionId, @Nullable ZonedDateTime submissionDate, @Nullable Boolean submitted,
            @Nullable SubmissionType submissionType, @Nullable Class<? extends Submission> submissionExerciseType, @Nullable Long resultId,
            @Nullable ZonedDateTime resultCompletionDate, @Nullable Double resultScore, @Nullable Boolean resultRated, @Nullable Boolean resultSuccessful,
            @Nullable AssessmentType resultAssessmentType, @Nullable Integer resultCodeIssueCount) {
        this(exerciseId, participationId, participationTypeName(participationType), initializationState, initializationDate, testRun, individualDueDate, presentationScore,
                repositoryUri, submissionId, submissionDate, submitted, submissionType, submissionTypeName(submissionExerciseType), resultId, resultCompletionDate, resultScore,
                resultRated, resultSuccessful, resultAssessmentType, resultCodeIssueCount);
    }

    public boolean isTestRun() {
        return Boolean.TRUE.equals(testRun);
    }

    public @Nullable ResultOverviewDTO toResultOverviewDTO() {
        return resultId == null ? null
                : new ResultOverviewDTO(resultId, resultCompletionDate, resultScore, resultRated, resultSuccessful, resultAssessmentType, resultCodeIssueCount);
    }

    public @Nullable SubmissionOverviewDTO toSubmissionOverviewDTO(List<ResultOverviewDTO> results) {
        return submissionId == null ? null
                : new SubmissionOverviewDTO(submissionId, submissionDate, Boolean.TRUE.equals(submitted), submissionType, submissionExerciseType, results);
    }

    private static String participationTypeName(Class<? extends Participation> participationType) {
        return participationType == ProgrammingExerciseStudentParticipation.class ? "programming" : "student";
    }

    private static @Nullable String submissionTypeName(@Nullable Class<? extends Submission> submissionType) {
        return switch (submissionType) {
            case Class<?> type when type == ProgrammingSubmission.class -> "programming";
            case Class<?> type when type == ModelingSubmission.class -> "modeling";
            case Class<?> type when type == QuizSubmission.class -> "quiz";
            case Class<?> type when type == TextSubmission.class -> "text";
            case Class<?> type when type == FileUploadSubmission.class -> "file-upload";
            case null -> null;
            default -> throw new IllegalArgumentException("Unsupported submission type: " + submissionType);
        };
    }
}
