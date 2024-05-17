package de.tum.in.www1.artemis.domain.iris.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

/**
 * An IrisTutorChatSession represents a conversation between a user and an LLM.
 * This is used for students receiving tutor assistance from Iris while working on an exercise.
 */
@Entity
@DiscriminatorValue("CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTutorChatSession extends IrisSession {

    @ManyToOne
    @JsonIgnore
    private Exercise exercise;

    @ManyToOne
    @JsonIgnore
    private User user;

    public IrisTutorChatSession() {
    }

    public IrisTutorChatSession(Exercise exercise, User user) {
        this.exercise = exercise;
        this.user = user;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
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
        return "IrisTutorChatSession{" + "id=" + getId() + ", exercise=" + (exercise == null ? "null" : exercise.getId()) + ", user=" + (user == null ? "null" : user.getName())
                + '}';
    }
}
