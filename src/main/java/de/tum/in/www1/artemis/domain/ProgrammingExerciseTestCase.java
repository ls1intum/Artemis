package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A ProgrammingExerciseTestCase.
 */
@Entity
@Table(name = "programming_exercise_test_case")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ProgrammingExerciseTestCase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ProgrammingExerciseTestCase id(Long id) {
        this.id = id;
        return this;
    }

    public String getTestName() {
        return testName;
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
     * this methods checks for database equality based on the id
     * @param o another object
     * @return whether this and the other object are equal based on the database id
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProgrammingExerciseTestCase testCase = (ProgrammingExerciseTestCase) o;
        if (testCase.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), exercise.getId());
    }

    /**
     * this methods checks for logical equality based on the name and the exercise
     * @param testCase another test case which should be checked for being the same
     * @return whether this and the other test case are the same based on name and exercise
     */
    public boolean isSameTestCase(ProgrammingExerciseTestCase testCase) {
        return testCase.getTestName().equals(this.getTestName()) && this.getExercise().getId().equals(testCase.getExercise().getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ProgrammingExerciseTestCase{" + "id=" + id + ", testName='" + testName + '\'' + ", weight=" + weight + ", active=" + active + ", afterDueDate=" + afterDueDate
                + ", bonusMultiplier=" + bonusMultiplier + ", bonusPoints=" + bonusPoints + '}';
    }
}
