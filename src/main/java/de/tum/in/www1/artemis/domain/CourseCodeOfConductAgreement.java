package de.tum.in.www1.artemis.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

/**
 * A user's agreement of a course's code of conduct.
 */
@Entity
@Table(name = "course_code_of_conduct_agreement")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseCodeOfConductAgreement extends DomainObject {

    @ManyToOne
    @JsonIncludeProperties({ "id" })
    @NotNull
    private Course course;

    @ManyToOne
    @JsonIncludeProperties({ "id" })
    @NotNull
    private User user;

    @Column(name = "is_accepted")
    private Boolean isAccepted;

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

    public Boolean getIsAccepted() {
        return isAccepted;
    }

    public void setIsAccepted(Boolean isAccepted) {
        this.isAccepted = isAccepted;
    }
}
