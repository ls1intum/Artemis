package de.tum.cit.aet.artemis.atlas.domain.science;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.course.domain.Course;

@Entity
@Table(name = "science_course_consent", uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "course_id" }) })
public class ScienceCourseConsent extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    private boolean active;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
