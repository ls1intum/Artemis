package de.tum.cit.aet.artemis.domain;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A user's agreement of a course's code of conduct.
 */
@Entity
@Table(name = "conduct_agreement")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@IdClass(ConductAgreementId.class)
public class ConductAgreement {

    @Id
    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConductAgreement that = (ConductAgreement) o;
        return course.equals(that.course) && user.equals(that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(course, user);
    }

    @Override
    public String toString() {
        return "ConductAgreement{" + "course=" + course + ", user=" + user + '}';
    }
}
