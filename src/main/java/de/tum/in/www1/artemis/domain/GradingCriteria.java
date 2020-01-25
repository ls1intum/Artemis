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
 * A  Grading Criteria that consists of structured grading instructions.
 */
@Entity
@Table(name = "grading_criteria")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class GradingCriteria implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "gradingCriteria", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JsonIgnoreProperties(value = "gradingCriteria", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<GradingInstruction> structuredGradingInstructions = new ArrayList<>();

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

    public GradingCriteria title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<GradingInstruction> getStructuredGradingInstructions() {
        return structuredGradingInstructions;
    }

    public GradingCriteria structuredGradingInstructions(List<GradingInstruction> structuredGradingInstructions) {
        this.structuredGradingInstructions = structuredGradingInstructions;
        return this;
    }

    public GradingCriteria addStructuredGradingInstructions(GradingInstruction structuredGradingInstruction) {
        this.structuredGradingInstructions.add(structuredGradingInstruction);
        structuredGradingInstruction.setGradingCriteria(this);
        return this;
    }

    public GradingCriteria removeStructuredGradingInstructions(GradingInstruction structuredGradingInstruction) {
        this.structuredGradingInstructions.remove(structuredGradingInstruction);
        structuredGradingInstruction.setGradingCriteria(null);
        return this;
    }

    public void setStructuredGradingInstructions(List<GradingInstruction> structuredGradingInstructions) {
        this.structuredGradingInstructions = structuredGradingInstructions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GradingCriteria gradingCriteria = (GradingCriteria) o;
        if (gradingCriteria.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), gradingCriteria.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "GradingCriteria{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", GradingInstructions='" + getStructuredGradingInstructions() + '}';
    }
}
