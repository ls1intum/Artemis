package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

@Profile(PROFILE_CORE)
@Controller
public class TextRepositoryApi extends AbstractTextApi {

    private final TextExerciseRepository textExerciseRepository;

    public TextRepositoryApi(TextExerciseRepository textExerciseRepository) {
        this.textExerciseRepository = textExerciseRepository;
    }

    public List<TextExercise> findAllWithCategoriesByCourseId(Long courseId) {
        return textExerciseRepository.findAllWithCategoriesByCourseId(courseId);
    }
}
