package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TextExerciseService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseService.class);

    private final TextExerciseRepository textExerciseRepository;
    private final ParticipationService participationService;

    public TextExerciseService(ParticipationService participationService,
                               TextExerciseRepository textExerciseRepository) {
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
}
