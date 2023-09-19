package de.tum.in.www1.artemis.domain;

import java.util.Objects;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A user's agreement of a course's code of conduct.
 */
@Entity
@Table(name = "course_code_of_conduct_agreement")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@IdClass(CourseCodeOfConductAgreementId.class)
public class CourseCodeOfConductAgreement {

    @Id
    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "is_accepted")
    private boolean isAccepted = false;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean getIsAccepted() {
        return isAccepted;
    }

    public void setIsAccepted(boolean isAccepted) {
        this.isAccepted = isAccepted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        CourseCodeOfConductAgreement that = (CourseCodeOfConductAgreement) o;
        return course.equals(that.course) && user.equals(that.user) && isAccepted == that.isAccepted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), course, user, isAccepted);
    }

    @Override
    public String toString() {
        return "CourseCodeOfConductAgreement{" + "course=" + course + ", user=" + user + ", isAccepted=" + isAccepted + '}';
    }
}
