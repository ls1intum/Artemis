package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A ModelingSubmission.
 */
@Entity
@DiscriminatorValue(value="M")
public class ModelingSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Transient
    @JsonProperty
    private String model;

    @Column(name = "explanation_text")
    @Lob
    private String explanationText;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public String getModel() {
        return model;
    }

    public ModelingSubmission model(String model) {
        this.model = model;
        return this;
    }

    public ModelingSubmission explanationText(String explanationText) {
        this.explanationText = explanationText;
        return this;
    }


    public ModelingSubmission() {
    }


    public void setModel(String model) {
        this.model = model;
    }

    public String getExplanationText() {
        return explanationText;
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
            "}";
    }
}
