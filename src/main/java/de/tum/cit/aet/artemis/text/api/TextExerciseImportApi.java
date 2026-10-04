package de.tum.cit.aet.artemis.text.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.service.TextExerciseImportService;

@Conditional(TextEnabled.class)
@Controller
@Lazy
public class TextExerciseImportApi extends AbstractTextApi {

    private final TextExerciseRepository textExerciseRepository;

    private final TextExerciseImportService textExerciseImportService;

    public TextExerciseImportApi(TextExerciseRepository textExerciseRepository, TextExerciseImportService textExerciseImportService) {
        this.textExerciseRepository = textExerciseRepository;
        this.textExerciseImportService = textExerciseImportService;
    }

    public Optional<TextExercise> findUniqueWithCompetenciesByTitleAndCourseId(String title, long courseId) throws NoUniqueQueryException {
        return textExerciseRepository.findUniqueWithCompetenciesByTitleAndCourseId(title, courseId);
    }

    public TextExercise findByIdWithExampleSubmissionsAndResultsAndGradingCriteriaElseThrow(long exerciseId) {
        return textExerciseRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteriaElseThrow(exerciseId);
    }

    public TextExercise importTextExercise(final TextExercise newExercise, final TextExercise sourceExercise) {
        return textExerciseImportService.importTextExercise(newExercise, sourceExercise);
    }

    public Optional<TextExercise> importTextExercise(final long sourceExerciseId, final TextExercise newExercise) {
        final Optional<TextExercise> optionalSourceExercise = textExerciseRepository.findWithExampleSubmissionsAndResultsAndGradingCriteriaById(sourceExerciseId);
        return optionalSourceExercise.map(sourceExercise -> textExerciseImportService.importTextExercise(newExercise, sourceExercise));
    }
}
