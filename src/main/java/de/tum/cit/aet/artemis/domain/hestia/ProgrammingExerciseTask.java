package de.tum.cit.aet.artemis.domain.hestia;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseTestCase;

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

    // No orphanRemoval here, as there should only be one parent-child relationship (which is ProgrammingExercise -> ExerciseHint)
    @OneToMany(mappedBy = "task", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("task")
    private Set<ExerciseHint> exerciseHints = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "programming_exercise_task_test_case", joinColumns = @JoinColumn(name = "task_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "test_case_id", referencedColumnName = "id"))
    @JsonIgnoreProperties(value = { "tasks", "exercise" }, allowSetters = true)
    private Set<ProgrammingExerciseTestCase> testCases = new HashSet<>();

    @ManyToOne
    @JsonIgnore
    private ProgrammingExercise exercise;

    public String getTaskName() {
        return this.taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Set<ExerciseHint> getExerciseHints() {
        return exerciseHints;
    }

    public void setExerciseHints(Set<ExerciseHint> exerciseHints) {
        this.exerciseHints = exerciseHints;
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
}
