package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * TODO: DELETE this class — replaced by {@link IrisChatSession} with exerciseId field. See Ticket 4.
 */
@Entity
@DiscriminatorValue("TEXT_EXERCISE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTextExerciseChatSession extends IrisChatSession {

    public IrisTextExerciseChatSession() {
    }

    public IrisTextExerciseChatSession(TextExercise exercise, User user) {
        super();
        setUserId(user.getId());
        setExerciseId(exercise.getId());
    }

    @Override
    public boolean shouldSelectLLMUsage() {
        return true;
    }

    @Override
    public IrisChatMode getMode() {
        return IrisChatMode.CHAT;
    }
}
