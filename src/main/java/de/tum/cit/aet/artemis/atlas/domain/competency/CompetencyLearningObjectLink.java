package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.MapsId;

import com.fasterxml.jackson.annotation.JsonIgnore;

@MappedSuperclass
public abstract class CompetencyLearningObjectLink implements Serializable {

    /**
     * The primary key of the association, composited through {@link CompetencyLearningObjectId}.
     */
    @EmbeddedId
    @JsonIgnore
    protected CompetencyLearningObjectId id = new CompetencyLearningObjectId();

    @ManyToOne
    @MapsId("competencyId")
    protected CourseCompetency competency;

    @Column(name = "link_weight")
    protected double weight;

    public CompetencyLearningObjectId getId() {
        return id;
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

    public CompetencyLearningObjectLink(CourseCompetency competency, double weight) {
        this.competency = competency;
        this.weight = weight;
    }

    public CompetencyLearningObjectLink() {
        // Empty constructor for Spring
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompetencyLearningObjectLink that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * This class is used to create a composite primary key (user_id, competency_id).
     * See also <a href="https://www.baeldung.com/spring-jpa-embedded-method-parameters">...</a>
     */
    @Embeddable
    public static class CompetencyLearningObjectId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private long learningObjectId;

        private long competencyId;

        public CompetencyLearningObjectId() {
            // Empty constructor for Spring
        }

        public CompetencyLearningObjectId(long learningObjectId, long competencyId) {
            this.learningObjectId = learningObjectId;
            this.competencyId = competencyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CompetencyLearningObjectId that)) {
                return false;
            }
            return learningObjectId == that.learningObjectId && competencyId == that.competencyId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(learningObjectId, competencyId);
        }
    }
}
