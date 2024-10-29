package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;

/**
 * This is a dto for updating a programming exercise test case.
 * It is only allowed to alter the weight, bonus multiplier, bonus points and visibility flag of a test case from an
 * endpoint, the other attributes are generated automatically.
 */
// TODO: convert to Record
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseTestCaseDTO {

    private Long id;

    private Double weight;

    private Double bonusMultiplier;

    private Double bonusPoints;

    private Visibility visibility;

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

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
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
