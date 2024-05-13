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
@DiscriminatorValue("CHAT") // Legacy. Should ideally be "TUTOR_CHAT"
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTutorChatSession extends IrisChatSession {

    @ManyToOne
    @JsonIgnore
    private Exercise exercise;

    public IrisTutorChatSession() {
    }

    public IrisTutorChatSession(Exercise exercise, User user) {
        super(user);
        this.exercise = exercise;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public String toString() {
        return "IrisTutorChatSession{" + "user=" + getUser().getLogin() + "," + "exercise=" + exercise + '}';
    }
}
