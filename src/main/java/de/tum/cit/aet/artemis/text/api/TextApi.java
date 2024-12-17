package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

@Controller
@Profile(PROFILE_CORE)
public class TextApi extends AbstractTextApi {

    private final TextExerciseRepository textExerciseRepository;

    private final TextBlockRepository textBlockRepository;

    public TextApi(TextExerciseRepository textExerciseRepository, TextBlockRepository textBlockRepository) {
        this.textExerciseRepository = textExerciseRepository;
        this.textBlockRepository = textBlockRepository;
    }

    public TextExercise findWithGradingCriteriaByIdElseThrow(long exerciseId) {
        return textExerciseRepository.findWithGradingCriteriaByIdElseThrow(exerciseId);
    }

    public TextBlock findById(String reference) {
        return textBlockRepository.findById(reference).orElse(null);
    }
}
