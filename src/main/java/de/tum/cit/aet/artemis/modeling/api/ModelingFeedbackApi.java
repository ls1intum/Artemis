package de.tum.cit.aet.artemis.modeling.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseFeedbackService;

/**
 * API for modeling feedback operations.
 */
@Conditional(ModelingEnabled.class)
@Controller
@Lazy
public class ModelingFeedbackApi extends AbstractModelingApi {

    private final ModelingExerciseFeedbackService modelingExerciseFeedbackService;

    public ModelingFeedbackApi(ModelingExerciseFeedbackService modelingExerciseFeedbackService) {
        this.modelingExerciseFeedbackService = modelingExerciseFeedbackService;
    }

    /**
     * Handles the request for generating non-graded feedback for a modeling exercise.
     *
     * @param participation    the student participation associated with the exercise
     * @param modelingExercise the modeling exercise object
     * @return the updated student participation
     */
    public StudentParticipation handleNonGradedFeedbackRequest(StudentParticipation participation, ModelingExercise modelingExercise) {
        return modelingExerciseFeedbackService.handleNonGradedFeedbackRequest(participation, modelingExercise);
    }

    /**
     * Triggers automatic Athena feedback for a modeling exercise submission inside a test exam. Soft-skips instead
     * of throwing when Athena is unavailable, the submission is missing/empty or already has an Athena result.
     *
     * @param participation    the student participation associated with the modeling exercise
     * @param modelingExercise the modeling exercise
     */
    public void generateAutomaticFeedbackForTestExamAsync(StudentParticipation participation, ModelingExercise modelingExercise) {
        modelingExerciseFeedbackService.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise);
    }
}
