package de.tum.in.www1.artemis.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingCriteria;
import de.tum.in.www1.artemis.repository.GradingCriteriaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Grading Criteria.
 */
@Service
public class GradingCriteriaService {

    private final Logger log = LoggerFactory.getLogger(GradingCriteriaService.class);

    private final GradingCriteriaRepository gradingCriteriaRepository;

    public GradingCriteriaService(GradingCriteriaRepository gradingCriteriaRepository) {
        this.gradingCriteriaRepository = gradingCriteriaRepository;
    }
    /**
     * Get one grading criteria by gradingCriteriaId.
     *
     * @param gradingCriteriaId the gradingCriteriaId of the entity
     * @return the entity
     */

    public GradingCriteria findOne(long gradingCriteriaId) {
        return gradingCriteriaRepository.findById(gradingCriteriaId)
                .orElseThrow(() -> new EntityNotFoundException("Grading Criteria with gradingCriteriaId  " + gradingCriteriaId + " does not exist!"));
    }
    /**
     * Get all exercise criteria belonging to exercise  with eager criteria.
     *
     * @param exerciseId the id of exercise
     * @return the list of criteria belonging to exercise
     */
    public List<GradingCriteria> findByExerciseIdWithEagerGradingCriteria(Long exerciseId) {
        return gradingCriteriaRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
    }

}
