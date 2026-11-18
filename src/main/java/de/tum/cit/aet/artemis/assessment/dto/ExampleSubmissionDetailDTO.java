package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextBlockDTO;

/**
 * Response of the example submission endpoints: the example submission with the content the editor pages render.
 * The example assessment is not part of it; the pages load it through the exercise type's example assessment endpoint.
 *
 * @param id                    the id of the example submission
 * @param usedForTutorial       whether tutors have to assess the example correctly before assessing students
 * @param assessmentExplanation the instructor's explanation of the example assessment
 * @param submission            the submitted content
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExampleSubmissionDetailDTO(Long id, boolean usedForTutorial, @Nullable String assessmentExplanation, @Nullable SubmissionContentDTO submission) {

    /**
     * The content of the example submission, flattened over the two supported submission types. {@code text},
     * {@code language} and {@code blocks} are set for text submissions, {@code model} and {@code explanationText}
     * for modeling submissions.
     *
     * @param id                     the id of the submission
     * @param submissionExerciseType the Jackson discriminator of the submission type ({@code text} or {@code modeling})
     * @param text                   the text of a text submission
     * @param language               the language of a text submission
     * @param blocks                 the text blocks of a text submission, null when they were not loaded
     * @param model                  the JSON model of a modeling submission
     * @param explanationText        the explanation of a modeling submission
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SubmissionContentDTO(Long id, String submissionExerciseType, @Nullable String text, @Nullable Language language, @Nullable List<TextBlockDTO> blocks,
            @Nullable String model, @Nullable String explanationText) {

        /**
         * Maps a submission to its content. Lazy collections that were not fetched stay null instead of triggering a load.
         *
         * @param submission the submission to map
         * @return the content, or null when the submission is null
         */
        @Nullable
        public static SubmissionContentDTO of(@Nullable Submission submission) {
            return switch (submission) {
                case null -> null;
                case TextSubmission textSubmission -> {
                    List<TextBlockDTO> blocks = Hibernate.isInitialized(textSubmission.getBlocks()) && textSubmission.getBlocks() != null
                            ? textSubmission.getBlocks().stream().map(TextBlockDTO::of).toList()
                            : null;
                    yield new SubmissionContentDTO(textSubmission.getId(), textSubmission.getSubmissionExerciseType(), textSubmission.getText(), textSubmission.getLanguage(),
                            blocks, null, null);
                }
                case ModelingSubmission modelingSubmission -> new SubmissionContentDTO(modelingSubmission.getId(), modelingSubmission.getSubmissionExerciseType(), null, null, null,
                        modelingSubmission.getModel(), modelingSubmission.getExplanationText());
                default -> new SubmissionContentDTO(submission.getId(), submission.getSubmissionExerciseType(), null, null, null, null, null);
            };
        }
    }

    /**
     * Maps an example submission to the response.
     *
     * @param exampleSubmission the example submission to map
     * @return the response
     */
    public static ExampleSubmissionDetailDTO of(ExampleSubmission exampleSubmission) {
        return new ExampleSubmissionDetailDTO(exampleSubmission.getId(), exampleSubmission.isUsedForTutorial(), exampleSubmission.getAssessmentExplanation(),
                SubmissionContentDTO.of(exampleSubmission.getSubmission()));
    }
}
