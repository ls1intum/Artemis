package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
public class GradingCriterion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "gradingCriterion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties(value = "gradingCriterion")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<GradingInstruction> structuredGradingInstructions = new ArrayList<>();

    @ManyToOne
    private Exercise exercise;

    @Column(name = "title")
    private String title;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GradingCriterion gradingCriterion = (GradingCriterion) o;
        if (gradingCriterion.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), gradingCriterion.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "GradingCriterion{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", GradingInstructions='" + getStructuredGradingInstructions() + '}';
    }
}
