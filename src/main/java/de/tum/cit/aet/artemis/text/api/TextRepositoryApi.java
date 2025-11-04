package de.tum.cit.aet.artemis.text.api;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseAthenaConfigService;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

@Conditional(TextEnabled.class)
@Controller
@Lazy
public class TextRepositoryApi extends AbstractTextApi {

    private final TextExerciseRepository textExerciseRepository;

    private final TextBlockRepository textBlockRepository;

    private final ExerciseAthenaConfigService exerciseAthenaConfigService;

    public TextRepositoryApi(TextExerciseRepository textExerciseRepository, TextBlockRepository textBlockRepository, ExerciseAthenaConfigService exerciseAthenaConfigService) {
        this.textExerciseRepository = textExerciseRepository;
        this.textBlockRepository = textBlockRepository;
        this.exerciseAthenaConfigService = exerciseAthenaConfigService;
    }

    public List<TextExercise> findAllWithCategoriesByCourseId(Long courseId) {
        return textExerciseRepository.findAllWithCategoriesByCourseId(courseId);
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

    public TextExercise findWithAthenaConfigByIdElseThrow(long exerciseId) {
        TextExercise exercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        exerciseAthenaConfigService.loadAthenaConfig(exercise);
        return exercise;
    }
}
