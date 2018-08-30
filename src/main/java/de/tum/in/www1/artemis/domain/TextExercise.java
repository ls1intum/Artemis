package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A TextExercise.
 */
@Entity
@DiscriminatorValue(value="T")
public class TextExercise extends Exercise implements Serializable {

    @Column(name = "sample_solution")
    @Lob
    private String sampleSolution;

    public String getSampleSolution() {
        return sampleSolution;
    }

    public TextExercise sampleSolution(String sampleSolution) {
        this.sampleSolution = sampleSolution;
        return this;
    }

    public void setSampleSolution(String sampleSolution) {
        this.sampleSolution = sampleSolution;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TextExercise textExercise = (TextExercise) o;
        if (textExercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), textExercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "TextExercise{" +
            "id=" + getId() +
            ", sampleSolution='" + getSampleSolution() + "'" +
            "}";
    }
}
