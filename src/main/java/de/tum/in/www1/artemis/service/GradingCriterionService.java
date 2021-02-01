package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
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

    /**
     * Calculates the points over all feedback elements that were set using structured grading instructions (SGI)
     *
     * @param feedback            feedback element that was set by SGI
     * @param inputPoints         totalPoints which is summed up.
     * @param gradingInstructions empty grading instruction Map to collect the used gradingInstructions
     * @return calculated total score from feedback elements set by SGI
     */
    public double computeTotalPoints(Feedback feedback, double inputPoints, Map<Long, Integer> gradingInstructions) {
        double totalPoints = inputPoints;
        if (gradingInstructions.get(feedback.getGradingInstruction().getId()) != null) {
            // We Encountered this grading instruction before
            var maxCount = feedback.getGradingInstruction().getUsageCount();
            var encounters = gradingInstructions.get(feedback.getGradingInstruction().getId());
            if (maxCount > 0) {
                if (encounters >= maxCount) {
                    // the structured grading instruction was applied on assessment models more often that the usageCount limit allows so we don't sum the feedback credit
                    gradingInstructions.put(feedback.getGradingInstruction().getId(), encounters + 1);
                }
                else {
                    // the usageCount limit was not exceeded yet so we add the credit and increase the nrOfEncounters counter
                    gradingInstructions.put(feedback.getGradingInstruction().getId(), encounters + 1);
                    totalPoints += feedback.getGradingInstruction().getCredits();
                }
            }
            else {
                totalPoints += feedback.getCredits();
            }
        }
        else {
            // First time encountering the grading instruction
            gradingInstructions.put(feedback.getGradingInstruction().getId(), 1);
            totalPoints += feedback.getCredits();
        }
        return totalPoints;
    }

}
