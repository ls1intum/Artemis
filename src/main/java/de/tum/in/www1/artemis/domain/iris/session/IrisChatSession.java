package de.tum.in.www1.artemis.domain.iris.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;

/**
 * An IrisChatSession represents a conversation between a user and an LLM.
 * This is used for students receiving tutor assistance from Iris while working on an exercise.
 */
@Entity
@DiscriminatorValue("CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisChatSession extends IrisSession {

    @ManyToOne
    @JsonIgnore
    private ProgrammingExercise exercise;

    @ManyToOne
    @JsonIgnore
    private User user;

    public IrisChatSession() {
    }

    public IrisChatSession(ProgrammingExercise exercise, User user) {
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
        return "IrisChatSession{" + "id=" + getId() + ", exercise=" + (exercise == null ? "null" : exercise.getId()) + ", user=" + (user == null ? "null" : user.getName()) + '}';
    }
}
