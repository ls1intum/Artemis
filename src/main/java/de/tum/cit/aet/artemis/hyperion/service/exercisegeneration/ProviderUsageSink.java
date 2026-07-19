package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.function.Consumer;

import org.springframework.ai.chat.model.ChatResponse;

/** Records successful provider usage and marks responses whose usage could not be accounted for. */
public interface ProviderUsageSink extends Consumer<ChatResponse> {

    /**
     * Marks token accounting as failed for a response that WAS received but whose usage could not be recorded
     * (metering hole on real spend). Thrown provider calls must not report here: they yield no response to meter,
     * their spend is bounded by the retry policy, and the caller's own error handling reports the failure.
     */
    void markUncertain();
}
