package de.tum.in.www1.artemis.domain;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A ModelingExercise.
 */
@Entity
@DiscriminatorValue(value="M")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ModelingExercise extends Exercise implements Serializable, SchedulableExercise {

    private static final long serialVersionUID = 1L;

    @Column(name = "description")
    private String description;

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove

    public String getDescription() {
        return description;
    }

    public ModelingExercise description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    public Boolean isEnded() {
        if (getDueDate() == null) {
            return false;
        }
        return ZonedDateTime.now().isAfter(getDueDate());
    }

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
            ", description='" + getDescription() + "'" +
            ", maxScore='" + getMaxScore() + "'" +
            "}";
    }
}
