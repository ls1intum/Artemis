package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;

// **
// * Service Implementation for managing Learning Goals.
// */
@Service
public class LearningGoalService {

    private final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final LearningGoalRepository learningGoalRepository;

    public LearningGoalService(LearningGoalRepository learningGoalRepository) {
        this.learningGoalRepository = learningGoalRepository;
    }

    /**
    * Saves a learning goal
    *
    * @param learningGoal the entity to save
    * @return the persisted entity
    */
    public LearningGoal save(LearningGoal learningGoal) {
        log.debug("Request to save learning goal: {}", learningGoal);
        return learningGoalRepository.save(learningGoal);
    }

}
