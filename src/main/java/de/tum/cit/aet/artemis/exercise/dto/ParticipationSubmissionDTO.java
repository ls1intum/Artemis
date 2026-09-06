package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Submission information nested in a participation response.
 *
 * @param id                     the unique identifier of the submission
 * @param submitted              whether the submission was submitted, if known
 * @param submissionDate         the submission time, if available
 * @param submissionExerciseType the exercise-type discriminator of the submission
 * @param commitHash             the programming commit hash, if this is a programming submission
 * @param text                   the submitted text when included for a start response
 * @param language               the submitted text language when included for a start response
 * @param model                  the submitted model when included for a start response
 * @param explanationText        the submitted model explanation when included for a start response
 * @param filePath               the submitted file path when included for a start response
 * @param results                initialized lean results, or absent when results were not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationSubmissionDTO(Long id, @Nullable Boolean submitted, @Nullable ZonedDateTime submissionDate, String submissionExerciseType, @Nullable String commitHash,
        @Nullable String text, @Nullable Language language, @Nullable String model, @Nullable String explanationText, @Nullable String filePath,
        @Nullable List<ParticipationSubmissionResultDTO> results) {

    /**
     * Creates a lean response DTO from a submission without initializing lazy results.
     *
     * @param submission the submission to map
     * @return the lean submission response
     */
    public static ParticipationSubmissionDTO of(Submission submission) {
        return of(submission, false);
    }

    /**
     * Creates a response DTO from a submission without initializing lazy results.
     *
     * @param submission     the submission to map
     * @param includeContent whether subtype-specific submission content should be included
     * @return the submission response
     */
    public static ParticipationSubmissionDTO of(Submission submission, boolean includeContent) {
        List<ParticipationSubmissionResultDTO> resultDTOs = null;
        if (Hibernate.isInitialized(submission.getResults())) {
            resultDTOs = submission.getResults().stream().filter(Objects::nonNull).map(ParticipationSubmissionResultDTO::of).toList();
        }
        String commitHash = submission instanceof ProgrammingSubmission programmingSubmission ? programmingSubmission.getCommitHash() : null;
        String text = includeContent && submission instanceof TextSubmission textSubmission ? textSubmission.getText() : null;
        Language language = includeContent && submission instanceof TextSubmission textSubmission ? textSubmission.getLanguage() : null;
        String model = includeContent && submission instanceof ModelingSubmission modelingSubmission ? modelingSubmission.getModel() : null;
        String explanationText = includeContent && submission instanceof ModelingSubmission modelingSubmission ? modelingSubmission.getExplanationText() : null;
        String filePath = includeContent && submission instanceof FileUploadSubmission fileUploadSubmission ? fileUploadSubmission.getFilePath() : null;
        return new ParticipationSubmissionDTO(submission.getId(), submission.isSubmitted(), submission.getSubmissionDate(), submission.getSubmissionExerciseType(), commitHash,
                text, language, model, explanationText, filePath, resultDTOs);
    }
}
