package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.service.TextExerciseImportService;

@Controller
@Profile(PROFILE_CORE)
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

    public TextExercise importTextExercise(final TextExercise templateExercise, TextExercise importedExercise) {
        return textExerciseImportService.importTextExercise(templateExercise, importedExercise);
    }

    public Optional<TextExercise> importTextExercise(final long templateExerciseId, final TextExercise exerciseToCopy) {
        final Optional<TextExercise> optionalOriginalTextExercise = textExerciseRepository.findWithExampleSubmissionsAndResultsById(templateExerciseId);
        return optionalOriginalTextExercise.map(textExercise -> textExerciseImportService.importTextExercise(textExercise, exerciseToCopy));
    }
}
