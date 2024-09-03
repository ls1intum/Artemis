package de.tum.in.www1.artemis.domain.iris.session;

import java.util.Optional;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;

/**
 * An IrisTextExerciseChatSession represents a conversation between a user and an LLM in the context of a text exercise.
 * This is used for students receiving tutor assistance from Iris while working on a text exercise.
 */
@Entity
@DiscriminatorValue("TEXT_EXERCISE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTextExerciseChatSession extends IrisChatSession {

    @ManyToOne
    @JsonIgnore
    private TextExercise exercise;

    public IrisTextExerciseChatSession() {
    }

    public IrisTextExerciseChatSession(TextExercise exercise, User user) {
        super(user);
        this.exercise = exercise;
    }

    public TextExercise getExercise() {
        return exercise;
    }

    public void setExercise(TextExercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public String toString() {
        return "IrisTextExerciseChatSession{" + "user=" + Optional.ofNullable(getUser()).map(User::getLogin).orElse("null") + "," + "exercise=" + exercise + '}';
    }
}
