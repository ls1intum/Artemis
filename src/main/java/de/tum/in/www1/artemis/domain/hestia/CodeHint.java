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

    // No CascadeType.REMOVE here, as we want to retain the solution entries when a code hint is deleted
    @OneToMany(mappedBy = "codeHint", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "codeHint" }, allowSetters = true)
    private Set<ProgrammingExerciseSolutionEntry> solutionEntries = new HashSet<>();

    public Set<ProgrammingExerciseSolutionEntry> getSolutionEntries() {
        return this.solutionEntries;
    }

    public void setSolutionEntries(Set<ProgrammingExerciseSolutionEntry> solutionEntries) {
        this.solutionEntries = solutionEntries;
    }

    /**
     * This method ensures that all solution entry references are removed before deleting a CodeHint
     */
    @PreRemove
    public void preRemove() {
        solutionEntries.forEach(solutionEntry -> solutionEntry.setCodeHint(null));
    }

    @Override
    public void removeContent() {
        super.removeContent();
        setSolutionEntries(new HashSet<>());
    }

    @Override
    public String toString() {
        return "CodeHint{" + "id=" + getId() + ", title='" + getTitle() + "}";
    }

    /**
     * Creates a copy of this hint including basic attributes, but excluding attributes referencing other models
     *
     * @return The copied hint
     */
    @Override
    public CodeHint createCopy() {
        CodeHint copiedHint = new CodeHint();

        copiedHint.setDescription(this.getDescription());
        copiedHint.setContent(this.getContent());
        copiedHint.setTitle(this.getTitle());
        return copiedHint;
    }
}
