package de.tum.in.www1.artemis.domain.text;

import java.util.List;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

/**
 * A TextExercise.
 */
@Entity
@DiscriminatorValue(value = "T")
public class TextExercise extends Exercise {

    @Column(name = "sample_solution")
    @Lob
    private String sampleSolution;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<TextCluster> clusters;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<TextPairwiseDistance> pairwiseDistances;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE)
    @OrderBy("child")
    @JsonIgnore
    private List<TextTreeNode> clusterTree;

    public String getSampleSolution() {
        return sampleSolution;
    }

    public void setSampleSolution(String sampleSolution) {
        this.sampleSolution = sampleSolution;
    }

    public TextExercise addPairwiseDistance(TextPairwiseDistance pairwiseDistance) {
        this.pairwiseDistances.add(pairwiseDistance);
        return this;
    }

    public void setPairwiseDistances(List<TextPairwiseDistance> pairwiseDistances) {
        this.pairwiseDistances = pairwiseDistances;
    }

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
