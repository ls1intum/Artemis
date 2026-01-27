package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * DTO for creating and updating example participations.
 * Supports both TextSubmission and ModelingSubmission types.
 *
 * @param id                      the id of the example participation (null for creation)
 * @param exerciseId              the id of the exercise this participation belongs to
 * @param usedForTutorial         whether this participation is used for tutorial
 * @param assessmentExplanation   explanation for the assessment
 * @param textSubmissionText      the text content (for TextSubmission)
 * @param modelingSubmissionModel the model JSON (for ModelingSubmission)
 * @param modelingExplanationText the explanation text (for ModelingSubmission)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExampleParticipationInputDTO(@Nullable Long id, @NotNull Long exerciseId, @Nullable Boolean usedForTutorial, @Nullable String assessmentExplanation,
        @Nullable String textSubmissionText, @Nullable String modelingSubmissionModel, @Nullable String modelingExplanationText) {

    /**
     * Creates an ExampleParticipationInputDTO from an ExampleParticipation entity.
     *
     * @param exampleParticipation the example participation entity
     * @return the corresponding DTO
     */
    public static ExampleParticipationInputDTO of(ExampleParticipation exampleParticipation) {
        Long exerciseId = exampleParticipation.getExercise() != null ? exampleParticipation.getExercise().getId() : null;
        String textSubmissionText = null;
        String modelingSubmissionModel = null;
        String modelingExplanationText = null;

        Submission submission = exampleParticipation.getSubmission();
        if (submission instanceof TextSubmission textSubmission) {
            textSubmissionText = textSubmission.getText();
        }
        else if (submission instanceof ModelingSubmission modelingSubmission) {
            modelingSubmissionModel = modelingSubmission.getModel();
            modelingExplanationText = modelingSubmission.getExplanationText();
        }

        return new ExampleParticipationInputDTO(exampleParticipation.getId(), exerciseId, exampleParticipation.isUsedForTutorial(), exampleParticipation.getAssessmentExplanation(),
                textSubmissionText, modelingSubmissionModel, modelingExplanationText);
    }

    /**
     * Creates a new ExampleParticipation entity from this DTO.
     *
     * @param exercise the exercise to associate with
     * @return a new ExampleParticipation entity
     */
    public ExampleParticipation toEntity(Exercise exercise) {
        ExampleParticipation exampleParticipation = new ExampleParticipation();
        exampleParticipation.setExercise(exercise);
        exampleParticipation.setUsedForTutorial(usedForTutorial);
        exampleParticipation.setAssessmentExplanation(assessmentExplanation);
        exampleParticipation.setInitializationState(InitializationState.INITIALIZED);

        // Create appropriate submission type based on which fields are present
        Submission submission = null;
        if (textSubmissionText != null) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.setText(textSubmissionText);
            textSubmission.setExampleSubmission(true);
            submission = textSubmission;
        }
        else if (modelingSubmissionModel != null) {
            ModelingSubmission modelingSubmission = new ModelingSubmission();
            modelingSubmission.setModel(modelingSubmissionModel);
            modelingSubmission.setExplanationText(modelingExplanationText);
            modelingSubmission.setExampleSubmission(true);
            submission = modelingSubmission;
        }

        if (submission != null) {
            exampleParticipation.addSubmission(submission);
        }

        return exampleParticipation;
    }

    /**
     * Applies this DTO's metadata to an existing ExampleParticipation entity.
     *
     * @param exampleParticipation the example participation to update
     */
    public void applyMetadataTo(ExampleParticipation exampleParticipation) {
        exampleParticipation.setUsedForTutorial(usedForTutorial);
        exampleParticipation.setAssessmentExplanation(assessmentExplanation);
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
