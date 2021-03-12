package de.tum.in.www1.artemis.domain;

import javax.annotation.Nonnull;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A ProgrammingExerciseTestCase.
 */
@Entity
@Table(name = "programming_exercise_test_case")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseTestCase extends DomainObject {

    @Column(name = "test_name")
    private String testName;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "after_due_date")
    private Boolean afterDueDate;

    @Column(name = "bonus_multiplier")
    private Double bonusMultiplier;

    @Column(name = "bonus_points")
    private Double bonusPoints;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("programmingExerciseTestCase")
    private ProgrammingExercise exercise;

    public ProgrammingExerciseTestCase id(Long id) {
        setId(id);
        return this;
    }

    public String getTestName() {
        return testName;
    }

    /**
     * We need to compare testcases via lowercase, because the testcaseRepository is case-insensitive
     *
     * @return testName as lowercase
     */
    public String getLowerCaseTestName() {
        return testName.toLowerCase();
    }

    public ProgrammingExerciseTestCase testName(String testName) {
        this.testName = testName;
        return this;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public Double getWeight() {
        return weight;
    }

    public ProgrammingExerciseTestCase weight(Double weight) {
        this.weight = weight;
        return this;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    @Nonnull
    public Double getBonusMultiplier() {
        return bonusMultiplier != null ? bonusMultiplier : 1.0;
    }

    public ProgrammingExerciseTestCase bonusMultiplier(Double bonusMultiplier) {
        this.bonusMultiplier = bonusMultiplier;
        return this;
    }

    public void setBonusMultiplier(Double bonusMultiplier) {
        this.bonusMultiplier = bonusMultiplier;
    }

    @Nonnull
    public Double getBonusPoints() {
        return bonusPoints != null ? bonusPoints : 0.0;
    }

    public ProgrammingExerciseTestCase bonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
        return this;
    }

    public void setBonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    public Boolean isActive() {
        return active;
    }

    public ProgrammingExerciseTestCase active(Boolean active) {
        this.active = active;
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public ProgrammingExerciseTestCase exercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    public Boolean isAfterDueDate() {
        if (afterDueDate == null) {
            return false;
        }
        return afterDueDate;
    }

    public void setAfterDueDate(Boolean afterDueDate) {
        this.afterDueDate = afterDueDate;
    }

    public ProgrammingExerciseTestCase afterDueDate(Boolean afterDueDate) {
        this.afterDueDate = afterDueDate;
        return this;
    }

    /**
     * This method needs to be checked and updated if there is a new class attribute. Creates a clone with all attributes set to the value of the object, including the id.
     *
     * @return a clone of the object.
     */
    public ProgrammingExerciseTestCase clone() {
        ProgrammingExerciseTestCase clone = new ProgrammingExerciseTestCase().testName(this.getTestName()).weight(this.getWeight()).active(this.isActive())
                .bonusPoints(this.getBonusPoints()).bonusMultiplier(this.getBonusMultiplier()).afterDueDate(afterDueDate).exercise(this.exercise);
        clone.setId(this.getId());
        return clone;
    }

    /**
     * this methods checks for logical equality based on the name and the exercise
     * @param testCase another test case which should be checked for being the same
     * @return whether this and the other test case are the same based on name and exercise
     */
    public boolean isSameTestCase(ProgrammingExerciseTestCase testCase) {
        return testCase.getLowerCaseTestName().equals(this.getLowerCaseTestName()) && this.getExercise().getId().equals(testCase.getExercise().getId());
    }

    @Override
    public String toString() {
        return "ProgrammingExerciseTestCase{" + "id=" + getId() + ", testName='" + testName + '\'' + ", weight=" + weight + ", active=" + active + ", afterDueDate=" + afterDueDate
                + ", bonusMultiplier=" + bonusMultiplier + ", bonusPoints=" + bonusPoints + '}';
    }
}
