package de.tum.cit.aet.artemis.core.domain;

import java.io.Serializable;

import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;

/** The user and course components of a course-scoped composite identifier. */
@MappedSuperclass
public abstract class UserCourseAssociation implements Serializable {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Parent
    protected User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @Parent
    protected Course course;

    public User getUser() {
        return user;
    }

    public Course getCourse() {
        return course;
    }

    protected void setUser(User user) {
        this.user = user;
    }

    protected void setCourse(Course course) {
        this.course = course;
    }
}
