package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

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

    @Column(name = "testName")
    private String testName;

    @Column(name = "weight")
    private Integer weight;

    @Column(name = "active")
    private Boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("programmingExerciseTestCase")
    private ProgrammingExercise exercise;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getWeight() {
        return weight;
    }

    public ProgrammingExerciseTestCase weight(Integer weight) {
        this.weight = weight;
        return this;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
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

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public ProgrammingExerciseTestCase clone() {
        ProgrammingExerciseTestCase clone = new ProgrammingExerciseTestCase().testName(this.getTestName()).weight(this.getWeight()).active(this.isActive()).exercise(this.exercise);
        clone.setId(this.getId());
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProgrammingExerciseTestCase programmingExerciseTestCase = (ProgrammingExerciseTestCase) o;
        if (programmingExerciseTestCase.getTestName().equals(this.getTestName()) && this.getExercise().getId().equals(programmingExerciseTestCase.getExercise().getId())) {
            return true;
        }
        if (getId() == null && programmingExerciseTestCase.getId() == null) {
            return false;
        }
        return Objects.equals(getId(), programmingExerciseTestCase.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(testName) + Objects.hashCode(getExercise().getId());
    }

    @Override
    public String toString() {
        return "ProgrammingExerciseTestCase{" + "id=" + getId() + ", testName='" + getTestName() + "'" + ", weight=" + getWeight() + ", active='" + isActive() + "'" + "}";
    }
}
