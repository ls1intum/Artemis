package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.MapsId;

@MappedSuperclass
public abstract class CompetencyLearningObjectLink implements Serializable {

    // Note: We intentionally do NOT use CascadeType.PERSIST here because competencies
    // are always saved before being linked to exercises/lecture units. Using cascade
    // would cause "detached entity passed to persist" errors when saving links with
    // already-persisted competencies.
    @ManyToOne(optional = false)
    @MapsId("competencyId")
    protected CourseCompetency competency;

    @Column(name = "link_weight")
    protected double weight;

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
}
