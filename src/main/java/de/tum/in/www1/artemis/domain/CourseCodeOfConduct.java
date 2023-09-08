package de.tum.in.www1.artemis.domain;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

@Entity
@Table(name = "course_code_of_conduct")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseCodeOfConduct extends DomainObject {

    @ManyToOne
    @JsonIncludeProperties({ "id" })
    @NotNull
    private Course course;

    @ManyToOne
    @JsonIncludeProperties({ "id" })
    @NotNull
    private User user;

    @Column(name = "is_code_of_conduct_accepted")
    private Boolean isCodeOfConductAccepted;

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

    public Boolean getIsCodeOfConductAccepted() {
        return isCodeOfConductAccepted;
    }

    public void setIsCodeOfConductAccepted(Boolean isCodeOfConductAccepted) {
        this.isCodeOfConductAccepted = isCodeOfConductAccepted;
    }
}
