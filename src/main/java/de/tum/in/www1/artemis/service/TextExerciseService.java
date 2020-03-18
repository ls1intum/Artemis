package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class TextExerciseService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseService.class);

    private final TextExerciseRepository textExerciseRepository;

    public TextExerciseService(TextExerciseRepository textExerciseRepository) {
        this.textExerciseRepository = textExerciseRepository;
    }

    /**
     * Get one quiz exercise by id.
     *
     * @param exerciseId the id of the exercise
     * @return the entity
     */
    public TextExercise findOne(long exerciseId) {
        log.debug("Request to get Text Exercise : {}", exerciseId);
        return textExerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise with id: \"" + exerciseId + "\" does not exist"));
    }

    /**
     * Find all exercises with *Due Date* in the future.
     *
     * @return List of Text Exercises
     */
    public List<TextExercise> findAllAutomaticAssessmentTextExercisesWithFutureDueDate() {
        return textExerciseRepository.findByAssessmentTypeAndDueDateIsAfter(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now());
    }
}
