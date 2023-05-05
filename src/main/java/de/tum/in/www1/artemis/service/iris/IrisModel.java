package de.tum.in.www1.artemis.service.iris;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;

import de.tum.in.www1.artemis.domain.iris.IrisSession;

/**
 * Represents a service that can be used to communicate with an AI model.
 */
@FunctionalInterface
public interface IrisModel {

    /**
     * Prompts the AI model to generate a response to the conversation in the provided {@link IrisSession}.
     *
     * @return The response from the LLM API
     */
    @Async
    CompletableFuture<String> getResponse(IrisSession session);

}
