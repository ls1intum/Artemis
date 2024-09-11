package de.tum.cit.aet.artemis.domain.iris.session;

import java.util.Optional;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.User;

/**
 * An IrisExerciseChatSession represents a conversation between a user and an LLM.
 * This is used for students receiving tutor assistance from Iris while working on an exercise.
 */
@Entity
@DiscriminatorValue("CHAT") // TODO: Legacy. Should ideally be "EXERCISE_CHAT"
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExerciseChatSession extends IrisChatSession {

    @ManyToOne
    @JsonIgnore
    private Exercise exercise;

    public IrisExerciseChatSession() {
    }

    public IrisExerciseChatSession(Exercise exercise, User user) {
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
        return "IrisExerciseChatSession{" + "user=" + Optional.ofNullable(getUser()).map(User::getLogin).orElse("null") + "," + "exercise=" + exercise + '}';
    }
}
