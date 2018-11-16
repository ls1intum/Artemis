package de.tum.in.www1.artemis.domain;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;
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

    /**
     * set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        setSampleSolution(null);
        super.filterSensitiveInformation();
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
