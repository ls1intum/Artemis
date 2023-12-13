package de.tum.in.www1.artemis.domain.iris.session;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;

/**
 * An IrisExerciseCreationSession represents a conversation between a user and Iris in the exercise creation window.
 * This is used for instructors receiving assistance from Iris while creating an exercise.
 */
@Entity
@DiscriminatorValue("EXERCISE_CREATION")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExerciseCreationSession extends IrisSession {

    @ManyToOne
    @JsonIgnore
    private Course course;

    @ManyToOne
    @JsonIgnore
    private User user;

    public IrisExerciseCreationSession() {
    }

    public IrisExerciseCreationSession(Course course, User user) {
        this.course = course;
        this.user = user;
    }

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
    public String toString() {
        return "IrisExerciseCreationSession{" + "id=" + getId() + ", user=" + (user == null ? "null" : user.getName()) + '}';
    }
}
