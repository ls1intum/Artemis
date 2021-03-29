package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for updating a programming exercise test case.
 * It is only allowed to alter the weight, bonus multiplier, bonus points and afterDueDate flag of a test case from an
 * endpoint, the other attributes are generated automatically.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseTestCaseDTO {

    private Long id;

    private Double weight;

    private Double bonusMultiplier;

    private Double bonusPoints;

    private boolean afterDueDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public boolean isAfterDueDate() {
        return afterDueDate;
    }

    public void setAfterDueDate(boolean afterDueDate) {
        this.afterDueDate = afterDueDate;
    }

    public Double getBonusMultiplier() {
        return bonusMultiplier;
    }

    public void setBonusMultiplier(Double bonusMultiplier) {
        this.bonusMultiplier = bonusMultiplier;
    }

    public Double getBonusPoints() {
        return bonusPoints;
    }

    public void setBonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
    }
}
