package de.tum.cit.aet.artemis.assessment.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * A Grading Criterion that consists of structured grading instructions.
 */
@Entity
@Table(name = "grading_criterion")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GradingCriterion extends DomainObject {

    @OneToMany(mappedBy = "gradingCriterion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties(value = "gradingCriterion", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<GradingInstruction> structuredGradingInstructions = new HashSet<>();

    @ManyToOne
    private Exercise exercise;

    @Column(name = "title")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Set<GradingInstruction> getStructuredGradingInstructions() {
        return structuredGradingInstructions;
    }

    public void addStructuredGradingInstruction(GradingInstruction structuredGradingInstruction) {
        this.structuredGradingInstructions.add(structuredGradingInstruction);
        structuredGradingInstruction.setGradingCriterion(this);
    }

    /**
     * @param structuredGradingInstructions the list of structured grading instructions which belong to the grading criterion
     */
    public void setStructuredGradingInstructions(Set<GradingInstruction> structuredGradingInstructions) {
        this.structuredGradingInstructions = structuredGradingInstructions;
        if (structuredGradingInstructions != null) {
            this.structuredGradingInstructions.forEach(structuredGradingInstruction -> structuredGradingInstruction.setGradingCriterion(this));
        }
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public String toString() {
        return "GradingCriterion{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", GradingInstructions='" + getStructuredGradingInstructions() + '}';
    }
}
