package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.text.service.TextExerciseService;

@Controller
@Profile(PROFILE_CORE)
public class TextApi extends AbstractTextApi {

    private final TextExerciseService textExerciseService;

    public TextApi(TextExerciseService textExerciseService) {
        this.textExerciseService = textExerciseService;
    }

    public void cancelScheduledOperations(long exerciseId) {
        textExerciseService.cancelScheduledOperations(exerciseId);
    }
}
