package de.tum.in.www1.artemis.web.rest.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.Result;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultWithPointsPerGradingCriterionDTO {

    private Result result;

    private Double totalPoints;

    /**
     * Map of {@link GradingCriterion#getId()} to the result points in that category.
     */
    private Map<Long, Double> pointsPerCriterion;

    public ResultWithPointsPerGradingCriterionDTO() {
        // needed for Jackson when executing tests
    }

    public ResultWithPointsPerGradingCriterionDTO(Result result, double totalPoints) {
        this(result, totalPoints, new HashMap<>());
    }

    public ResultWithPointsPerGradingCriterionDTO(Result result, double totalPoints, Map<Long, Double> pointsPerCriterion) {
        this.result = result;
        this.totalPoints = totalPoints;
        this.pointsPerCriterion = pointsPerCriterion;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Double getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(Double totalPoints) {
        this.totalPoints = totalPoints;
    }

    public Map<Long, Double> getPointsPerCriterion() {
        return pointsPerCriterion;
    }

    public void setPointsPerCriterion(Map<Long, Double> pointsPerCriterion) {
        this.pointsPerCriterion = pointsPerCriterion;
    }
}
