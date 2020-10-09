package de.tum.in.www1.artemis.service;

import java.util.Set;

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
     * Finds all learning goals associated with a course
     *
     * @param courseId id of the course
     * @return set of learning goals associated with a course
     */
    public Set<LearningGoal> findAllByCourseId(Long courseId) {
        return learningGoalRepository.findAllByCourseId(courseId);
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
