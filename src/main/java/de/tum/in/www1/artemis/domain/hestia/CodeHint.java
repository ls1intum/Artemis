package de.tum.in.www1.artemis.domain.hestia;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ExerciseHint;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTask;

/**
 * A CodeHint.
 */
@Entity
@Table(name = "code_hint")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CodeHint extends ExerciseHint {

    @OneToMany(mappedBy = "codeHint", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("codeHint")
    private Set<SolutionEntry> solutionEntries = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("codeHint")
    private ProgrammingExerciseTask task;

    public Set<SolutionEntry> getSolutionEntries() {
        return this.solutionEntries;
    }

    public CodeHint solutionEntries(Set<SolutionEntry> solutionEntries) {
        this.solutionEntries = solutionEntries;
        return this;
    }

    public void setSolutionEntries(Set<SolutionEntry> solutionEntries) {
        this.solutionEntries = solutionEntries;
    }

    public ProgrammingExerciseTask getProgrammingExerciseTask() {
        return task;
    }

    public CodeHint programmingExerciseTask(ProgrammingExerciseTask programmingExerciseTask) {
        this.task = programmingExerciseTask;
        return this;
    }

    public void setProgrammingExerciseTask(ProgrammingExerciseTask programmingExerciseTask) {
        this.task = programmingExerciseTask;
    }
}
