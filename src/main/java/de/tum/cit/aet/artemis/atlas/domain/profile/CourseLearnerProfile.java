package de.tum.cit.aet.artemis.atlas.domain.profile;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "course_learner_profile")
public class CourseLearnerProfile extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "learner_profile_id")
    private LearnerProfile learnerProfile;

    @OneToOne
    @JoinColumn(name = "course_id")
    private Course course;

    public void setLearnerProfile(LearnerProfile learnerProfile) {
        this.learnerProfile = learnerProfile;
    }

    public LearnerProfile getLearnerProfile() {
        return this.learnerProfile;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Course getCourse() {
        return this.course;
    }
}
