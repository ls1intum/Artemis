package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.GradingCriteria;
import de.tum.in.www1.artemis.repository.GradingCriteriaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


/**
 * Service Implementation for managing Grading Criteria.
 */
@Service
public class GradingCriteriaService {
    private final Logger log = LoggerFactory.getLogger(GradingInstructionService.class);

    private final GradingCriteriaRepository gradingCriteriaRepository;

    public GradingCriteriaService(GradingCriteriaRepository gradingCriteriaRepository) {
        this.gradingCriteriaRepository = gradingCriteriaRepository;
    }
    /**
     * Save a grading criteria.
     *
     * @param gradingCriteria the entity to save
     * @return the persisted entity
     */
    public GradingCriteria save(GradingCriteria gradingCriteria) {
        log.debug("Request to save Grading Criteria : {}", gradingCriteria);
        return gradingCriteriaRepository.save(gradingCriteria);

    }

    /**
     * Delete the grading criteria by id.
     * @param gradingCriteria the grading instruction to be deleted
     */

    public void delete(GradingCriteria gradingCriteria) {
        log.info("GradingInstructionService.Request to delete Grading Instruction : {}", gradingCriteria.getId());
        gradingCriteriaRepository.delete(gradingCriteria);
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

}
