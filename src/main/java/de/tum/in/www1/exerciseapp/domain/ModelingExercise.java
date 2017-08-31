package de.tum.in.www1.exerciseapp.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A ModelingExercise.
 */
@Entity
@DiscriminatorValue(value="M")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ModelingExercise extends Exercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "base_file_path")
    private String baseFilePath;

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove

    public String getBaseFilePath() {
        return baseFilePath;
    }

    public ModelingExercise baseFilePath(String baseFilePath) {
        this.baseFilePath = baseFilePath;
        return this;
    }

    public void setBaseFilePath(String baseFilePath) {
        this.baseFilePath = baseFilePath;
    }
    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

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
            ", baseFilePath='" + getBaseFilePath() + "'" +
            "}";
    }
}
