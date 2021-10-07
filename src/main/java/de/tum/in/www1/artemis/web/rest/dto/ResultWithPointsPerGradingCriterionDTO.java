package de.tum.in.www1.artemis.web.rest.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.Result;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultWithPointsPerGradingCriterionDTO {

    private Result result;

    /**
     * Map of {@link GradingCriterion#getId()} to the result points in that category.
     */
    private Map<Long, Double> points = new HashMap<>();

    public ResultWithPointsPerGradingCriterionDTO() {
    }

    public ResultWithPointsPerGradingCriterionDTO(Result result, Map<Long, Double> points) {
        this.result = result;
        this.points = points;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Map<Long, Double> getPoints() {
        return points;
    }

    public void setPoints(Map<Long, Double> points) {
        this.points = points;
    }
}
