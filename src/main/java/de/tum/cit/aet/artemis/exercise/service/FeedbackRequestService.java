package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.api.ModelingFeedbackApi;
import de.tum.cit.aet.artemis.modeling.config.ModelingApiNotPresentException;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCodeReviewFeedbackService;
import de.tum.cit.aet.artemis.text.api.TextFeedbackApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Service for processing non-graded feedback requests for different exercise types.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class FeedbackRequestService {

    private final Optional<ProgrammingExerciseCodeReviewFeedbackService> programmingExerciseCodeReviewFeedbackService;

    private final Optional<ModelingFeedbackApi> modelingFeedbackApi;

    private final Optional<TextFeedbackApi> textFeedbackApi;

    public FeedbackRequestService(Optional<ProgrammingExerciseCodeReviewFeedbackService> programmingExerciseCodeReviewFeedbackService,
            Optional<ModelingFeedbackApi> modelingFeedbackApi, Optional<TextFeedbackApi> textFeedbackApi) {
        this.programmingExerciseCodeReviewFeedbackService = programmingExerciseCodeReviewFeedbackService;
        this.modelingFeedbackApi = modelingFeedbackApi;
        this.textFeedbackApi = textFeedbackApi;
    }

    /**
     * Processes a non-graded feedback request for the given exercise and participation by delegating
     * to the appropriate exercise-type-specific feedback handler (text, modeling, or programming).
     *
     * @param exercise      the exercise for which feedback is requested
     * @param participation the student participation that the feedback request is based on
     * @return the updated {@link StudentParticipation} after the feedback request has been processed
     * @throws UnsupportedOperationException if the exercise type does not support non-graded feedback requests
     */
    public StudentParticipation processFeedbackRequest(Exercise exercise, StudentParticipation participation) {
        return switch (exercise) {
            case TextExercise textExercise -> {
                TextFeedbackApi api = textFeedbackApi.orElseThrow(() -> new TextApiNotPresentException(TextFeedbackApi.class));
                yield api.handleNonGradedFeedbackRequest(participation, textExercise);
            }
            case ModelingExercise modelingExercise -> {
                ModelingFeedbackApi api = modelingFeedbackApi.orElseThrow(() -> new ModelingApiNotPresentException(ModelingFeedbackApi.class));
                yield api.handleNonGradedFeedbackRequest(participation, modelingExercise);
            }
            case ProgrammingExercise programmingExercise ->
                programmingExerciseCodeReviewFeedbackService.orElseThrow(() -> new UnsupportedOperationException("ProgrammingExerciseCodeReviewFeedbackService is not available"))
                        .handleNonGradedFeedbackRequest(exercise.getId(), (ProgrammingExerciseStudentParticipation) participation, programmingExercise);
            default -> throw new UnsupportedOperationException("Non-graded feedback requests are not supported for exercise type: " + exercise.getClass().getSimpleName());
        };
    }
}
