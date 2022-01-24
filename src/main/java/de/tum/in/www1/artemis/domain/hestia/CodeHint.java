package de.tum.in.www1.artemis.domain.hestia;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A CodeHint.
 */
@Entity
@DiscriminatorValue("C")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CodeHint extends ExerciseHint {

    @OneToMany(mappedBy = "codeHint", cascade = { CascadeType.MERGE, CascadeType.PERSIST }, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("codeHint")
    private Set<ProgrammingExerciseSolutionEntry> solutionEntries = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("codeHint")
    private ProgrammingExerciseTask task;

    public Set<ProgrammingExerciseSolutionEntry> getSolutionEntries() {
        return this.solutionEntries;
    }

    public void setSolutionEntries(Set<ProgrammingExerciseSolutionEntry> solutionEntries) {
        this.solutionEntries = solutionEntries;
    }

    public CodeHint solutionEntries(Set<ProgrammingExerciseSolutionEntry> solutionEntries) {
        setSolutionEntries(solutionEntries);
        return this;
    }

    public ProgrammingExerciseTask getProgrammingExerciseTask() {
        return task;
    }

    public void setProgrammingExerciseTask(ProgrammingExerciseTask programmingExerciseTask) {
        this.task = programmingExerciseTask;
    }

    public CodeHint programmingExerciseTask(ProgrammingExerciseTask programmingExerciseTask) {
        setProgrammingExerciseTask(programmingExerciseTask);
        return this;
    }

    /**
     * This method ensures that all solution entry references are removed before deleting a CodeHint
     */
    @PreRemove
    private void preRemove() {
        solutionEntries.forEach(solutionEntry -> solutionEntry.setCodeHint(null));
    }

    @Override
    public String toString() {
        return "CodeHint{" + "id=" + getId() + ", title='" + getTitle() + "}";
    }
}
