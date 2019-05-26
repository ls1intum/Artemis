package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.in.www1.artemis.domain.enumeration.TestCaseType;

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

    @Column(name = "fileName")
    private String fileName;

    @Column(name = "testName")
    private String testName;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private TestCaseType type;

    @Column(name = "weight")
    private Integer weight;

    @ManyToOne
    private ProgrammingExercise exercise;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public ProgrammingExerciseTestCase file_name(String file_name) {
        this.fileName = file_name;
        return this;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getTestName() {
        return testName;
    }

    public ProgrammingExerciseTestCase test_name(String test_name) {
        this.testName = test_name;
        return this;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public TestCaseType getType() {
        return type;
    }

    public ProgrammingExerciseTestCase type(TestCaseType type) {
        this.type = type;
        return this;
    }

    public void setType(TestCaseType type) {
        this.type = type;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProgrammingExerciseTestCase programmingExerciseTestCase = (ProgrammingExerciseTestCase) o;
        if (programmingExerciseTestCase.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), programmingExerciseTestCase.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ProgrammingExerciseTestCase{" + "id=" + getId() + ", fileName='" + getFileName() + "'" + ", testName='" + getTestName() + "'" + ", type='" + getType() + "'"
                + ", weight=" + getWeight() + "}";
    }
}
