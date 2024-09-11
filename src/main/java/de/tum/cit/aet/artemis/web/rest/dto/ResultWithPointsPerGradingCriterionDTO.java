package de.tum.cit.aet.artemis.web.rest.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;

/**
 * @param pointsPerCriterion Map of {@link GradingCriterion#getId()} to the result points in that category.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultWithPointsPerGradingCriterionDTO(Result result, Double totalPoints, Map<Long, Double> pointsPerCriterion) {
}
