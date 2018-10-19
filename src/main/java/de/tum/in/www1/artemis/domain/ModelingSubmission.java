package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A ModelingSubmission.
 */
@Entity
@Table(name = "modeling_submission")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ModelingSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model")
    private String model;

    @Column(name = "explanation_text")
    private String explanationText;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public ModelingSubmission model(String model) {
        this.model = model;
        return this;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getExplanationText() {
        return explanationText;
    }

    public ModelingSubmission explanationText(String explanationText) {
        this.explanationText = explanationText;
        return this;
    }

    public void setExplanationText(String explanationText) {
        this.explanationText = explanationText;
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
        ModelingSubmission modelingSubmission = (ModelingSubmission) o;
        if (modelingSubmission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), modelingSubmission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ModelingSubmission{" +
            "id=" + getId() +
            ", model='" + getModel() + "'" +
            ", explanationText='" + getExplanationText() + "'" +
            "}";
    }
}
