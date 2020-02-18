package de.tum.in.www1.artemis.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.repository.GradingCriterionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Grading Criteria.
 */
@Service
public class GradingCriterionService {

    private final Logger log = LoggerFactory.getLogger(GradingCriterionService.class);

    private final GradingCriterionRepository gradingCriterionRepository;

    public GradingCriterionService(GradingCriterionRepository gradingCriterionRepository) {
        this.gradingCriterionRepository = gradingCriterionRepository;
    }

    /**
     * Get one grading criterion by gradingCriterionId.
     *
     * @param gradingCriterionId the gradingCriterionId of the entity
     * @return the entity
     */

    public GradingCriterion findOne(long gradingCriterionId) {
        return gradingCriterionRepository.findById(gradingCriterionId)
                .orElseThrow(() -> new EntityNotFoundException("Grading Criterion with gradingCriterionId  " + gradingCriterionId + " does not exist!"));
    }

    /**
     * Get all exercise criteria belonging to exercise  with eager criteria.
     *
     * @param exerciseId the id of exercise
     * @return the list of criteria belonging to exercise
     */
    public List<GradingCriterion> findByExerciseIdWithEagerGradingCriteria(long exerciseId) {
        return gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
    }

}
