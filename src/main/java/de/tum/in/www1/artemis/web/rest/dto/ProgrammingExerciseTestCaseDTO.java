package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.enumeration.TestCaseVisibility;

/**
 * This is a dto for updating a programming exercise test case.
 * It is only allowed to alter the weight, bonus multiplier, bonus points and afterDueDate flag of a test case from an
 * endpoint, the other attributes are generated automatically.
 */
public class ProgrammingExerciseTestCaseDTO {

    private Long id;

    private Double weight;

    private Double bonusMultiplier;

    private Double bonusPoints;

    // private boolean afterDueDate;

    private TestCaseVisibility visibility;

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

    public TestCaseVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(TestCaseVisibility visibility) {
        this.visibility = visibility;
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
