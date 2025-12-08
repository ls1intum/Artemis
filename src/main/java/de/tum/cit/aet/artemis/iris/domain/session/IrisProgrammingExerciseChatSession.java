package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * An IrisProgrammingExerciseChatSession represents a conversation between a user and an LLM.
 * This is used for students receiving tutor assistance from Iris while working on an exercise.
 */
@Entity
@DiscriminatorValue("PROGRAMMING_EXERCISE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisProgrammingExerciseChatSession extends IrisChatSession {

    @JsonIgnore
    private long exerciseId;

    public IrisProgrammingExerciseChatSession() {
    }

    public IrisProgrammingExerciseChatSession(Exercise exercise, User user) {
        super(user);
        this.exerciseId = exercise.getId();
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    @Override
    public boolean shouldSelectLLMUsage() {
        return true;
    }

    @Override
    public IrisChatMode getMode() {
        return IrisChatMode.PROGRAMMING_EXERCISE_CHAT;
    }
}
