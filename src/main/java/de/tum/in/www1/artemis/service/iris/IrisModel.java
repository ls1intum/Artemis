package de.tum.in.www1.artemis.service.iris;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;

import de.tum.in.www1.artemis.domain.iris.IrisSession;

/**
 * Represents a service that sends requests to a LLM API.
 */
@FunctionalInterface
public interface IrisModel {

    /**
     * Sends a request to the LLM API.
     * This method attempts to convert the provided body to a JSON string using Jackson.
     * If this fails, an internal server error is returned.
     *
     * @return The response from the LLM API
     */
    @Async
    CompletableFuture<String> getResponse(IrisSession session);

}
