package de.tum.cit.aet.artemis.text.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.service.TextExerciseFeedbackService;

@Conditional(TextEnabled.class)
@Controller
@Lazy
public class TextFeedbackApi extends AbstractTextApi {

    private final TextExerciseFeedbackService feedbackService;

    public TextFeedbackApi(TextExerciseFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    public StudentParticipation handleNonGradedFeedbackRequest(StudentParticipation participation, TextExercise textExercise) {
        return feedbackService.handleNonGradedFeedbackRequest(participation, textExercise);
    }

    /**
     * Triggers automatic Athena feedback for a text exercise submission inside a test exam. Soft-skips instead of
     * throwing when Athena is unavailable, the submission is missing/empty or already has an Athena result.
     *
     * @param participation the student participation associated with the text exercise
     * @param textExercise  the text exercise
     */
    public void generateAutomaticFeedbackForTestExamAsync(StudentParticipation participation, TextExercise textExercise) {
        feedbackService.generateAutomaticFeedbackForTestExamAsync(participation, textExercise);
    }
}
