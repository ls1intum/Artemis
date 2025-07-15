package de.tum.cit.aet.artemis.text.api;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

@ConditionalOnProperty(name = "artemis.text.enabled", havingValue = "true")
@Controller
public class TextRepositoryApi extends AbstractTextApi {

    private final TextExerciseRepository textExerciseRepository;

    private final TextBlockRepository textBlockRepository;

    public TextRepositoryApi(TextExerciseRepository textExerciseRepository, TextBlockRepository textBlockRepository) {
        this.textExerciseRepository = textExerciseRepository;
        this.textBlockRepository = textBlockRepository;
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
}
