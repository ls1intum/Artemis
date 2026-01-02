package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * DTO for creating and updating example submissions.
 * Supports both TextSubmission and ModelingSubmission types.
 *
 * @param id                      the id of the example submission (null for creation)
 * @param exerciseId              the id of the exercise this submission belongs to
 * @param usedForTutorial         whether this submission is used for tutorial
 * @param assessmentExplanation   explanation for the assessment
 * @param textSubmissionText      the text content (for TextSubmission)
 * @param modelingSubmissionModel the model JSON (for ModelingSubmission)
 * @param modelingExplanationText the explanation text (for ModelingSubmission)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExampleSubmissionInputDTO(@Nullable Long id, @NotNull Long exerciseId, @Nullable Boolean usedForTutorial, @Nullable String assessmentExplanation,
        @Nullable String textSubmissionText, @Nullable String modelingSubmissionModel, @Nullable String modelingExplanationText) {

    /**
     * Creates an ExampleSubmissionInputDTO from an ExampleSubmission entity.
     *
     * @param exampleSubmission the example submission entity
     * @return the corresponding DTO
     */
    public static ExampleSubmissionInputDTO of(ExampleSubmission exampleSubmission) {
        Long exerciseId = exampleSubmission.getExercise() != null ? exampleSubmission.getExercise().getId() : null;
        String textSubmissionText = null;
        String modelingSubmissionModel = null;
        String modelingExplanationText = null;

        Submission submission = exampleSubmission.getSubmission();
        if (submission instanceof TextSubmission textSubmission) {
            textSubmissionText = textSubmission.getText();
        }
        else if (submission instanceof ModelingSubmission modelingSubmission) {
            modelingSubmissionModel = modelingSubmission.getModel();
            modelingExplanationText = modelingSubmission.getExplanationText();
        }

        return new ExampleSubmissionInputDTO(exampleSubmission.getId(), exerciseId, exampleSubmission.isUsedForTutorial(), exampleSubmission.getAssessmentExplanation(),
                textSubmissionText, modelingSubmissionModel, modelingExplanationText);
    }

    /**
     * Creates a new ExampleSubmission entity from this DTO.
     *
     * @param exercise the exercise to associate with
     * @return a new ExampleSubmission entity
     */
    public ExampleSubmission toEntity(Exercise exercise) {
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setExercise(exercise);
        exampleSubmission.setUsedForTutorial(usedForTutorial);
        exampleSubmission.setAssessmentExplanation(assessmentExplanation);

        // Create appropriate submission type based on which fields are present
        if (textSubmissionText != null) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.setText(textSubmissionText);
            textSubmission.setExampleSubmission(true);
            exampleSubmission.setSubmission(textSubmission);
        }
        else if (modelingSubmissionModel != null) {
            ModelingSubmission modelingSubmission = new ModelingSubmission();
            modelingSubmission.setModel(modelingSubmissionModel);
            modelingSubmission.setExplanationText(modelingExplanationText);
            modelingSubmission.setExampleSubmission(true);
            exampleSubmission.setSubmission(modelingSubmission);
        }

        return exampleSubmission;
    }

    /**
     * Applies this DTO's metadata to an existing ExampleSubmission entity.
     *
     * @param exampleSubmission the example submission to update
     */
    public void applyMetadataTo(ExampleSubmission exampleSubmission) {
        exampleSubmission.setUsedForTutorial(usedForTutorial);
        exampleSubmission.setAssessmentExplanation(assessmentExplanation);
    }

    /**
     * Updates the submission content based on this DTO.
     *
     * @param existingSubmission the existing submission to update
     */
    public void applySubmissionContentTo(Submission existingSubmission) {
        if (existingSubmission instanceof TextSubmission textSubmission && textSubmissionText != null) {
            textSubmission.setText(textSubmissionText);
        }
        else if (existingSubmission instanceof ModelingSubmission modelingSubmission) {
            if (modelingSubmissionModel != null) {
                modelingSubmission.setModel(modelingSubmissionModel);
            }
            if (modelingExplanationText != null) {
                modelingSubmission.setExplanationText(modelingExplanationText);
            }
        }
    }
}
