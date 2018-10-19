package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.enumeration.DiagramType;

/**
 * A ModelingExercise.
 */
@Entity
@Table(name = "modeling_exercise")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ModelingExercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagram_type")
    private DiagramType diagramType;

    @Column(name = "sample_solution_model")
    private String sampleSolutionModel;

    @Column(name = "sample_solution_explanation")
    private String sampleSolutionExplanation;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public ModelingExercise diagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
        return this;
    }

    public void setDiagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
    }

    public String getSampleSolutionModel() {
        return sampleSolutionModel;
    }

    public ModelingExercise sampleSolutionModel(String sampleSolutionModel) {
        this.sampleSolutionModel = sampleSolutionModel;
        return this;
    }

    public void setSampleSolutionModel(String sampleSolutionModel) {
        this.sampleSolutionModel = sampleSolutionModel;
    }

    public String getSampleSolutionExplanation() {
        return sampleSolutionExplanation;
    }

    public ModelingExercise sampleSolutionExplanation(String sampleSolutionExplanation) {
        this.sampleSolutionExplanation = sampleSolutionExplanation;
        return this;
    }

    public void setSampleSolutionExplanation(String sampleSolutionExplanation) {
        this.sampleSolutionExplanation = sampleSolutionExplanation;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModelingExercise modelingExercise = (ModelingExercise) o;
        if (modelingExercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), modelingExercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ModelingExercise{" +
            "id=" + getId() +
            ", diagramType='" + getDiagramType() + "'" +
            ", sampleSolutionModel='" + getSampleSolutionModel() + "'" +
            ", sampleSolutionExplanation='" + getSampleSolutionExplanation() + "'" +
            "}";
    }
}
