package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.hestia.CodeHint;

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

    @OneToMany(mappedBy = "task", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("programmingExerciseTask")
    private Set<CodeHint> codeHints = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "programming_exercise_task_test_case", joinColumns = @JoinColumn(name = "task_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "test_case_id", referencedColumnName = "id"))
    @JsonIgnoreProperties("tasks")
    private Set<ProgrammingExerciseTestCase> testCases = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("programmingExerciseTask")
    private ProgrammingExercise exercise;

    public String getTaskName() {
        return this.taskName;
    }

    public ProgrammingExerciseTask taskName(String taskName) {
        this.taskName = taskName;
        return this;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Set<CodeHint> getCodeHints() {
        return codeHints;
    }

    public ProgrammingExerciseTask codeHints(Set<CodeHint> codeHints) {
        this.codeHints = codeHints;
        return this;
    }

    public void setCodeHints(Set<CodeHint> codeHints) {
        this.codeHints = codeHints;
    }

    public Set<ProgrammingExerciseTestCase> getTestCases() {
        return this.testCases;
    }

    public ProgrammingExerciseTask testCases(Set<ProgrammingExerciseTestCase> testCases) {
        this.testCases = testCases;
        return this;
    }

    public void setTestCases(Set<ProgrammingExerciseTestCase> testCases) {
        this.testCases = testCases;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public ProgrammingExerciseTask exercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }
}
