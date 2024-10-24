package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "competency_lecture_unit")
public class CompetencyLectureUnitLink extends CompetencyLearningObjectLink {

    @EmbeddedId
    @JsonIgnore
    protected CompetencyLectureUnitId id = new CompetencyLectureUnitId();

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @MapsId("lectureUnitId")
    private LectureUnit lectureUnit;

    public CompetencyLectureUnitLink(CourseCompetency competency, LectureUnit lectureUnit, double weight) {
        super(competency, weight);
        this.lectureUnit = lectureUnit;
    }

    public CompetencyLectureUnitLink() {
        // Empty constructor for Spring
    }

    public LectureUnit getLectureUnit() {
        return lectureUnit;
    }

    public void setLectureUnit(LectureUnit lectureUnit) {
        this.lectureUnit = lectureUnit;
    }

    public CompetencyLectureUnitId getId() {
        return id;
    }

    @Override
    public String toString() {
        return "CompetencyLectureUnitLink{" + "lectureUnit=" + lectureUnit + ", id=" + id + ", competency=" + competency + ", weight=" + weight + '}';
    }

    @Embeddable
    public static class CompetencyLectureUnitId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private long lectureUnitId;

        private long competencyId;

        public CompetencyLectureUnitId() {
            // Empty constructor for Spring
        }

        public CompetencyLectureUnitId(long lectureUnitId, long competencyId) {
            this.lectureUnitId = lectureUnitId;
            this.competencyId = competencyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CompetencyLectureUnitId that)) {
                return false;
            }
            return lectureUnitId == that.lectureUnitId && competencyId == that.competencyId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lectureUnitId, competencyId);
        }

        @Override
        public String toString() {
            return "CompetencyLectureUnitId{" + "lectureUnitId=" + lectureUnitId + ", competencyId=" + competencyId + '}';
        }
    }
}
