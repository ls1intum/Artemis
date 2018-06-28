package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

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

    @Column(name = "base_file_path")
    private String baseFilePath;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
            ", baseFilePath='" + getBaseFilePath() + "'" +
            "}";
    }
}
