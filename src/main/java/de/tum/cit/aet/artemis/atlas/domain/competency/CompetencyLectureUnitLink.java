package de.tum.cit.aet.artemis.atlas.domain.competency;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "competency_lecture_unit")
public class CompetencyLectureUnitLink extends CompetencyLearningObjectLink {

    @ManyToOne
    @MapsId("learningObjectId")
    private LectureUnit lectureUnit;

    public LectureUnit getLectureUnit() {
        return lectureUnit;
    }

    public void setLectureUnit(LectureUnit lectureUnit) {
        this.lectureUnit = lectureUnit;
    }

    public CompetencyLectureUnitLink(CourseCompetency competency, LectureUnit lectureUnit, double weight) {
        super(competency, weight);
        this.lectureUnit = lectureUnit;
    }

    public CompetencyLectureUnitLink() {
        // Empty constructor for Spring
    }

    @Override
    public String toString() {
        return "CompetencyLectureUnitLink{" + "lectureUnit=" + lectureUnit + ", id=" + id + ", competency=" + competency + ", weight=" + weight + '}';
    }
}
