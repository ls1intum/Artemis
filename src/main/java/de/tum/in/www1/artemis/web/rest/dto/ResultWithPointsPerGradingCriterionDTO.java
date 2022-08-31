package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.Result;

/**
 * @param pointsPerCriterion Map of {@link GradingCriterion#getId()} to the result points in that category.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultWithPointsPerGradingCriterionDTO(Result result, Double totalPoints, Map<Long, Double> pointsPerCriterion) {
}
