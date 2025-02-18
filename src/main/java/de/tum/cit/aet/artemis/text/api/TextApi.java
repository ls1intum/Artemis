package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.service.TextExerciseService;

@Controller
@Profile(PROFILE_CORE)
public class TextApi extends AbstractTextApi {

    private final TextExerciseRepository textExerciseRepository;

    private final TextBlockRepository textBlockRepository;

    private final TextExerciseService textExerciseService;

    // TextExerciseService needs to be initialized lazy because of the dependency to InstanceMessageSendService
    public TextApi(TextExerciseRepository textExerciseRepository, TextBlockRepository textBlockRepository, @Lazy TextExerciseService textExerciseService) {
        this.textExerciseRepository = textExerciseRepository;
        this.textBlockRepository = textBlockRepository;
        this.textExerciseService = textExerciseService;
    }

    public TextExercise findWithGradingCriteriaByIdElseThrow(long exerciseId) {
        return textExerciseRepository.findWithGradingCriteriaByIdElseThrow(exerciseId);
    }

    public Optional<TextBlock> findById(String reference) {
        return textBlockRepository.findById(reference);
    }

    public TextExercise findByIdElseThrow(long exerciseId) {
        return textExerciseRepository.findByIdElseThrow(exerciseId);
    }

    public List<TextExercise> findAllWithCategoriesByCourseId(Long courseId) {
        return textExerciseRepository.findAllWithCategoriesByCourseId(courseId);
    }

    public void cancelScheduledOperations(long exerciseId) {
        textExerciseService.cancelScheduledOperations(exerciseId);
    }

    public void save(TextExercise exercise) {
        textExerciseRepository.save(exercise);
    }
}
