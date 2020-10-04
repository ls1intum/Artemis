package de.tum.in.www1.artemis.domain;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A  Grading Criterion that consists of structured grading instructions.
 */
@Entity
@Table(name = "grading_criterion")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class GradingCriterion extends DomainObject {

    @OneToMany(mappedBy = "gradingCriterion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties(value = "gradingCriterion", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<GradingInstruction> structuredGradingInstructions = new ArrayList<>();

    @ManyToOne
    private Exercise exercise;

    @Column(name = "title")
    private String title;

    public String getTitle() {
        return title;
    }

    public GradingCriterion title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<GradingInstruction> getStructuredGradingInstructions() {
        return structuredGradingInstructions;
    }

    public GradingCriterion structuredGradingInstructions(List<GradingInstruction> structuredGradingInstructions) {
        setStructuredGradingInstructions(structuredGradingInstructions);
        return this;
    }

    public GradingCriterion addStructuredGradingInstructions(GradingInstruction structuredGradingInstruction) {
        this.structuredGradingInstructions.add(structuredGradingInstruction);
        structuredGradingInstruction.setGradingCriterion(this);
        return this;
    }

    public GradingCriterion removeStructuredGradingInstructions(GradingInstruction structuredGradingInstruction) {
        this.structuredGradingInstructions.remove(structuredGradingInstruction);
        structuredGradingInstruction.setGradingCriterion(null);
        return this;
    }

    /**
     * @param structuredGradingInstructions the list of structured grading instructions which belong to the grading criterion
     */
    public void setStructuredGradingInstructions(List<GradingInstruction> structuredGradingInstructions) {
        this.structuredGradingInstructions = structuredGradingInstructions;
        if (structuredGradingInstructions != null) {
            this.structuredGradingInstructions.forEach(structuredGradingInstruction -> {
                structuredGradingInstruction.setGradingCriterion(this);
            });
        }
    }

    public Exercise getExercise() {
        return exercise;
    }

    public GradingCriterion exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public String toString() {
        return "GradingCriterion{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", GradingInstructions='" + getStructuredGradingInstructions() + '}';
    }
}
