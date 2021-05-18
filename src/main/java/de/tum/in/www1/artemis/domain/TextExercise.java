package de.tum.in.www1.artemis.domain;

import java.util.List;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

/**
 * A TextExercise.
 */
@Entity
@DiscriminatorValue(value = "T")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextExercise extends Exercise {

    @Column(name = "sample_solution")
    @Lob
    private String sampleSolution;

    @Column(name = "imported_exercise_id")
    private Long importedExerciseId;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<TextCluster> clusters;

    public String getSampleSolution() {
        return sampleSolution;
    }

    public void setSampleSolution(String sampleSolution) {
        this.sampleSolution = sampleSolution;
    }

    public Long getImportedExerciseId() { return importedExerciseId;}

    public void setImportedExerciseId(Long importedExerciseId) { this.importedExerciseId = importedExerciseId; }

    public boolean isAutomaticAssessmentEnabled() {
        return getAssessmentType() == AssessmentType.SEMI_AUTOMATIC;
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
    public String toString() {
        return "TextExercise{" + "id=" + getId() + ", sampleSolution='" + getSampleSolution() + "'" + "}";
    }

}
