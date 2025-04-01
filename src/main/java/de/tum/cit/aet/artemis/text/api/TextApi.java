package de.tum.cit.aet.artemis.text.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.text.config.TextExerciseEnabled;
import de.tum.cit.aet.artemis.text.service.TextExerciseService;

@Controller
@Conditional(TextExerciseEnabled.class)
public class TextApi extends AbstractTextApi {

    private final TextExerciseService textExerciseService;

    public TextApi(TextExerciseService textExerciseService) {
        this.textExerciseService = textExerciseService;
    }

    public void cancelScheduledOperations(long exerciseId) {
        textExerciseService.cancelScheduledOperations(exerciseId);
    }
}
