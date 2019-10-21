package de.tum.in.www1.artemis.web.rest.dto;

/**
 * This is a dto for updating a programming exercise test case.
 * It is only allowed to alter the weight and afterDueDate flag of a test case from an endpoint, the other attributes are generated automatically.
 */
public class ProgrammingExerciseTestCaseDTO {

    private Long id;

    private Integer weight;

    private boolean afterDueDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public boolean isAfterDueDate() {
        return afterDueDate;
    }

    public void setAfterDueDate(boolean afterDueDate) {
        this.afterDueDate = afterDueDate;
    }
}
