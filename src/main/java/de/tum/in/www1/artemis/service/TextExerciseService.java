package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;

@Service
@Transactional
public class TextExerciseService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseService.class);

    private final TextExerciseRepository textExerciseRepository;

    private final ParticipationService participationService;

    public TextExerciseService(TextExerciseRepository textExerciseRepository, ParticipationService participationService) {

        this.textExerciseRepository = textExerciseRepository;
        this.participationService = participationService;
    }

    /**
     * Get one quiz exercise by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public TextExercise findOne(Long id) {
        log.debug("Request to get Text Exercise : {}", id);
        return textExerciseRepository.findById(id).get();
    }

    /**
     * Delete the text exercise by id.
     *
     * @param id the id of the entity
     */
    @Transactional
    public void delete(Long id) {
        log.debug("Request to delete Text Exercise : {}", id);
        // delete all participations belonging to this text exercise
        participationService.deleteAllByExerciseId(id, false, false);
        textExerciseRepository.deleteById(id);
    }

    /**
     * Find all exercises with *Due Date* in the future.
     *
     * @return List of Text Exercises
     */
    @Transactional(readOnly = true)
    public List<TextExercise> findAllAutomaticAssessmentTextExercisesWithFutureDueDate() {
        return textExerciseRepository.findByAssessmentTypeAndDueDateIsAfter(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now());
    }
}
