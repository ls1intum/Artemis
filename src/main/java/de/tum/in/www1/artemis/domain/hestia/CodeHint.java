package de.tum.in.www1.artemis.domain.hestia;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTask;

/**
 * A CodeHint.
 */
@Entity
@DiscriminatorValue("C")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CodeHint extends ExerciseHint {

    @OneToMany(mappedBy = "codeHint", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("codeHint")
    private Set<ProgrammingExerciseSolutionEntry> solutionEntries = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("codeHint")
    private ProgrammingExerciseTask task;

    public Set<ProgrammingExerciseSolutionEntry> getSolutionEntries() {
        return this.solutionEntries;
    }

    public CodeHint solutionEntries(Set<ProgrammingExerciseSolutionEntry> solutionEntries) {
        this.solutionEntries = solutionEntries;
        return this;
    }

    public void setSolutionEntries(Set<ProgrammingExerciseSolutionEntry> solutionEntries) {
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

    @Override
    public String toString() {
        return "CodeHint{" + "id=" + getId() + ", title='" + getTitle() + "}";
    }
}
