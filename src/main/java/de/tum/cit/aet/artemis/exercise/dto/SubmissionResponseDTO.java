package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.AbstractQuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * DTO representing a polymorphic submission REST response.
 *
 * @param id                     the submission identifier
 * @param submitted              whether the submission was submitted
 * @param type                   the submission trigger type, if available
 * @param exampleSubmission      whether this is an example submission, if specified
 * @param submissionDate         the submission date, if available
 * @param durationInMinutes      the duration between participation start and submission, if available
 * @param submissionExerciseType the polymorphic exercise-type discriminator
 * @param participation          a DTO-safe participation reference without submissions, if available
 * @param results                initialized result DTOs, or absent if results were not loaded
 * @param commitHash             the programming commit hash, if applicable
 * @param buildFailed            whether the programming build failed, if applicable
 * @param text                   the submitted text, if applicable
 * @param language               the text language, if applicable
 * @param model                  the submitted model, if applicable
 * @param explanationText        the modeling explanation, if applicable
 * @param filePath               the uploaded file path, if applicable
 * @param quizBatch              the quiz batch identifier, if applicable
 * @param scoreInPoints          the quiz score in points, if applicable
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionResponseDTO(Long id, Boolean submitted, @Nullable SubmissionType type, @Nullable Boolean exampleSubmission, @Nullable ZonedDateTime submissionDate,
        @Nullable Long durationInMinutes, String submissionExerciseType, @Nullable StudentParticipationDTO participation, @Nullable List<SubmissionResultDTO> results,
        @Nullable String commitHash, @Nullable Boolean buildFailed, @Nullable String text, @Nullable Language language, @Nullable String model, @Nullable String explanationText,
        @Nullable String filePath, @Nullable Long quizBatch, @Nullable Double scoreInPoints) implements Serializable {

    /**
     * Maps a submission for participation history, retaining visible participant data and initialized assessors.
     *
     * @param submission the authorized history submission to map
     * @return the submission response DTO
     */
    public static SubmissionResponseDTO ofForParticipationHistory(Submission submission) {
        return of(submission, true, false, false);
    }

    /**
     * Maps the final prepared test-run submission, retaining initialized feedback and test-run context.
     *
     * @param submission the prepared test-run submission to map
     * @return the submission response DTO
     */
    public static SubmissionResponseDTO ofForTestRunAssessment(Submission submission) {
        return of(submission, true, true, false);
    }

    /**
     * Maps a submission for example-submission import, retaining participant display data and content used for the size preview.
     *
     * @param submission the import candidate to map
     * @return the submission response DTO
     */
    public static SubmissionResponseDTO ofForImport(Submission submission) {
        return of(submission, true, false, true);
    }

    /**
     * Maps an already anonymized submission for the complaint assessment dashboard.
     *
     * @param submission the filtered complaint submission to map
     * @return the submission response DTO
     */
    public static SubmissionResponseDTO ofForComplaintDashboard(Submission submission) {
        return of(submission, false, false, false);
    }

    /**
     * Maps an already-filtered submission for exercise details, retaining initialized result details while omitting the participation back-reference.
     *
     * @param submission the authorized and filtered exercise-details submission to map
     * @return the submission response DTO
     */
    public static SubmissionResponseDTO ofForExerciseDetails(Submission submission) {
        return of(submission, false, true, false);
    }

    private static SubmissionResponseDTO of(Submission submission, boolean includeParticipant, boolean includeFeedback, boolean latestResultOnly) {
        Objects.requireNonNull(submission, "The submission must be set");

        StudentParticipationDTO participation = Hibernate.isInitialized(submission.getParticipation())
                && submission.getParticipation() instanceof StudentParticipation studentParticipation ? StudentParticipationDTO.of(studentParticipation, includeParticipant) : null;
        List<SubmissionResultDTO> results = null;
        if (Hibernate.isInitialized(submission.getResults())) {
            if (latestResultOnly) {
                var latestResult = submission.getLatestResult();
                results = latestResult != null ? List.of(SubmissionResultDTO.of(latestResult, includeFeedback)) : List.of();
            }
            else {
                results = submission.getResults().stream().filter(Objects::nonNull).map(result -> SubmissionResultDTO.of(result, includeFeedback)).toList();
            }
        }

        String commitHash = null;
        Boolean buildFailed = null;
        String text = null;
        Language language = null;
        String model = null;
        String explanationText = null;
        String filePath = null;
        Long quizBatch = null;
        Double scoreInPoints = null;

        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            commitHash = programmingSubmission.getCommitHash();
            buildFailed = programmingSubmission.isBuildFailed();
        }
        else if (submission instanceof TextSubmission textSubmission) {
            text = textSubmission.getText();
            language = textSubmission.getLanguage();
        }
        else if (submission instanceof ModelingSubmission modelingSubmission) {
            model = modelingSubmission.getModel();
            explanationText = modelingSubmission.getExplanationText();
        }
        else if (submission instanceof FileUploadSubmission fileUploadSubmission) {
            filePath = fileUploadSubmission.getFilePath();
        }
        else if (submission instanceof QuizSubmission quizSubmission) {
            quizBatch = quizSubmission.getQuizBatch();
            scoreInPoints = quizSubmission.getScoreInPoints();
        }
        else if (submission instanceof AbstractQuizSubmission quizSubmission) {
            scoreInPoints = quizSubmission.getScoreInPoints();
        }

        Long durationInMinutes = submission.getParticipation() == null || Hibernate.isInitialized(submission.getParticipation()) ? submission.getDurationInMinutes() : null;
        return new SubmissionResponseDTO(submission.getId(), submission.isSubmitted(), submission.getType(), submission.isExampleSubmission(), submission.getSubmissionDate(),
                durationInMinutes, submission.getSubmissionExerciseType(), participation, results, commitHash, buildFailed, text, language, model, explanationText, filePath,
                quizBatch, scoreInPoints);
    }
}
