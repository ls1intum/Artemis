package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.MapsId;

import com.fasterxml.jackson.annotation.JsonProperty;

@MappedSuperclass
public abstract class CompetencyLearningObjectLink implements Serializable {

    @ManyToOne(optional = false)
    @MapsId("competencyId")
    protected CourseCompetency competency;

    @Column(name = "link_weight")
    protected double weight;

    @Column(name = "generated_by_ai", nullable = false)
    protected boolean generatedByAi;

    public CompetencyLearningObjectLink(CourseCompetency competency, double weight) {
        this.competency = competency;
        this.weight = weight;
    }

    public CompetencyLearningObjectLink() {
        // Empty constructor for Spring
    }

    public CourseCompetency getCompetency() {
        return competency;
    }

    public void setCompetency(CourseCompetency competency) {
        this.competency = competency;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isGeneratedByAi() {
        return generatedByAi;
    }

    public void setGeneratedByAi(boolean generatedByAi) {
        this.generatedByAi = generatedByAi;
    }
}
