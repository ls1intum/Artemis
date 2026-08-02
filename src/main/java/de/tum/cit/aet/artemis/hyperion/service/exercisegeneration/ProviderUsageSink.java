package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.function.Consumer;

import org.springframework.ai.chat.model.ChatResponse;

/** Records provider usage and marks any admitted provider attempt whose usage cannot be proved. */
public interface ProviderUsageSink extends Consumer<ChatResponse> {

    /** Records tool calls requested by the model, whether or not execution succeeds. */
    void recordToolCalls(long count);

    /** Records a turn when it starts, including turns whose session produces no result. */
    default void recordTurn() {
    }

    /** Records an authoring attempt when it starts. */
    default void recordAttempt() {
    }

    /** Marks accounting incomplete after an admitted provider call whose usage cannot be proved. */
    void markUncertain();
}
