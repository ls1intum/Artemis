package de.tum.in.www1.artemis.domain.hestia;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;

/**
 * A ProgrammingExerciseTask
 */
@Entity
@Table(name = "programming_exercise_task")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseTask extends DomainObject {

    @Column(name = "task_name")
    private String taskName;

    // No orphanRemoval here, as there should only be one parent-child relationship (which is ProgrammingExercise -> CodeHint)
    @OneToMany(mappedBy = "task", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("task")
    private Set<CodeHint> codeHints = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "programming_exercise_task_test_case", joinColumns = @JoinColumn(name = "task_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "test_case_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "tasks", "exercise" })
    private Set<ProgrammingExerciseTestCase> testCases = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties("tasks")
    private ProgrammingExercise exercise;

    public String getTaskName() {
        return this.taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Set<CodeHint> getCodeHints() {
        return codeHints;
    }

    public void setCodeHints(Set<CodeHint> codeHints) {
        this.codeHints = codeHints;
    }

    public Set<ProgrammingExerciseTestCase> getTestCases() {
        return this.testCases;
    }

    public void setTestCases(Set<ProgrammingExerciseTestCase> testCases) {
        this.testCases = testCases;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public String toString() {
        return "ProgrammingExerciseTask{" + "taskName='" + taskName + '\'' + ", testCases=" + testCases + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        if (!super.equals(other)) {
            return false;
        }
        ProgrammingExerciseTask that = (ProgrammingExerciseTask) other;
        return Objects.equals(taskName, that.taskName) && Objects.equals(testCases, that.testCases);
    }
}
