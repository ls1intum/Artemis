package de.tum.in.www1.artemis.service.iris.model;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisSession;

/**
 * Represents a service that can be used to communicate with an AI model.
 */
public interface IrisModel {

    /**
     * Sends a request to the AI model and returns the response.
     *
     * @param session The session to send the request for.
     * @return A CompletableFuture that will be completed with the response.
     */
    @Async
    CompletableFuture<IrisMessage> getResponse(IrisSession session);

    /**
     * Returns the initial system message for the respective LLM
     *
     * @return Initial system message
     */
    String getInitialSystemMessageTemplate();
}
