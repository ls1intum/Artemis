package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * A submission with its results, as the exercise details nest it under a participation and the participation
 * submissions list it.
 * <p>
 * {@code submissionExerciseType} is the Jackson discriminator of the {@code Submission} hierarchy, so it is always set.
 * The subtype components are set for the matching submission type only, and every scalar the entity serialized is
 * repeated. The lazy collections of the subtypes (text blocks, build log entries, submitted answers) are never fetched on
 * these routes, so the entity left them off the wire and this record does not carry them.
 *
 * @param id                     the id of the submission
 * @param submissionExerciseType the submission discriminator: {@code programming}, {@code modeling}, {@code quiz}, {@code text} or {@code file-upload}
 * @param submitted              whether the submission is submitted
 * @param type                   the submission type
 * @param exampleSubmission      whether it is an example submission
 * @param submissionDate         when it was submitted
 * @param durationInMinutes      the minutes between the start of the participation and the submission
 * @param empty                  whether the submission is empty; the quiz submission never serialized it
 * @param results                the results, where the route fetched them
 * @param participation          the participation, on the participation submissions only
 * @param text                   the text, for text submissions
 * @param language               the detected language, for text submissions
 * @param model                  the model, for modeling submissions
 * @param explanationText        the explanation, for modeling submissions
 * @param filePath               the uploaded file, for file upload submissions
 * @param commitHash             the commit hash, for programming submissions
 * @param buildFailed            whether the build failed, for programming submissions
 * @param scoreInPoints          the score in points, for quiz submissions
 * @param quizBatch              the id of the batch, for quiz submissions
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DetailedSubmissionDTO(Long id, String submissionExerciseType, @Nullable Boolean submitted, @Nullable SubmissionType type, @Nullable Boolean exampleSubmission,
        @Nullable ZonedDateTime submissionDate, @Nullable Long durationInMinutes, @Nullable Boolean empty, @Nullable List<DetailedResultDTO> results,
        @Nullable DetailedParticipationDTO participation, @Nullable String text, @Nullable Language language, @Nullable String model, @Nullable String explanationText,
        @Nullable String filePath, @Nullable String commitHash, @Nullable Boolean buildFailed, @Nullable Double scoreInPoints, @Nullable Long quizBatch) {

    /**
     * Maps an already filtered submission.
     *
     * @param submission    the submission to map
     * @param participation the participation to nest, or {@code null} where the submission sits under its participation
     * @return the submission as the payloads carry it
     */
    public static DetailedSubmissionDTO of(Submission submission, @Nullable DetailedParticipationDTO participation) {
        String text = null;
        Language language = null;
        String model = null;
        String explanationText = null;
        String filePath = null;
        String commitHash = null;
        Boolean buildFailed = null;
        Double scoreInPoints = null;
        Long quizBatch = null;
        switch (submission) {
            case TextSubmission textSubmission -> {
                text = textSubmission.getText();
                language = textSubmission.getLanguage();
            }
            case ModelingSubmission modelingSubmission -> {
                model = modelingSubmission.getModel();
                explanationText = modelingSubmission.getExplanationText();
            }
            case FileUploadSubmission fileUploadSubmission -> filePath = fileUploadSubmission.getFilePath();
            case ProgrammingSubmission programmingSubmission -> {
                commitHash = programmingSubmission.getCommitHash();
                buildFailed = programmingSubmission.isBuildFailed();
            }
            case QuizSubmission quizSubmission -> {
                scoreInPoints = quizSubmission.getScoreInPoints();
                quizBatch = quizSubmission.getQuizBatch();
            }
            // the cases above have to stay in sync with the @JsonSubTypes list on Submission
            default -> throw new IllegalArgumentException("Unsupported submission type: " + submission.getClass().getName());
        }
        // QuizSubmission#isEmpty is @JsonIgnore, every other subtype serialized it
        Boolean empty = submission instanceof QuizSubmission ? null : submission.isEmpty();
        List<DetailedResultDTO> results = Hibernate.isInitialized(submission.getResults())
                ? submission.getResults().stream().filter(Objects::nonNull).map(DetailedResultDTO::of).toList()
                : null;
        return new DetailedSubmissionDTO(submission.getId(), submission.getSubmissionExerciseType(), submission.isSubmitted(), submission.getType(),
                submission.isExampleSubmission(), submission.getSubmissionDate(), submission.getDurationInMinutes(), empty, results, participation, text, language, model,
                explanationText, filePath, commitHash, buildFailed, scoreInPoints, quizBatch);
    }
}
