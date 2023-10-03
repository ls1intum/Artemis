package de.tum.in.www1.artemis.domain.iris.session;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;

/**
 * An IrisCodeEditorSession represents a conversation between a user and Iris in the Code Editor.
 * This is used for instructors receiving assistance from Iris while editing an exercise.
 */
@Entity
@DiscriminatorValue("CODE_EDITOR")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCodeEditorSession extends IrisSession {

    @ManyToOne
    @JsonIgnore
    private ProgrammingExercise exercise;

    @ManyToOne
    @JsonIgnore
    private User user;

    public IrisCodeEditorSession() {
    }

    public IrisCodeEditorSession(ProgrammingExercise exercise, User user) {
        this.exercise = exercise;
        this.user = user;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "IrisCodeEditorSession{" + "id=" + getId() + ", exercise=" + (exercise == null ? "null" : exercise.getId()) + ", user=" + (user == null ? "null" : user.getName())
                + '}';
    }
}
