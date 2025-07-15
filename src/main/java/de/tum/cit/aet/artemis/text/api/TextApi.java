package de.tum.cit.aet.artemis.text.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.text.service.TextExerciseService;

@ConditionalOnProperty(name = "artemis.text.enabled", havingValue = "true")
@Controller
public class TextApi extends AbstractTextApi {

    private final TextExerciseService textExerciseService;

    public TextApi(TextExerciseService textExerciseService) {
        this.textExerciseService = textExerciseService;
    }

    public void cancelScheduledOperations(long exerciseId) {
        textExerciseService.cancelScheduledOperations(exerciseId);
    }
}
