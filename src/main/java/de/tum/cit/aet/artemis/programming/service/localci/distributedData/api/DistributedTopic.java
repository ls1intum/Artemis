package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api;

import java.util.UUID;
import java.util.function.Consumer;

public interface DistributedTopic<T> {

    /**
     * Publishes a message to the topic.
     *
     * @param message the message to publish
     */
    void publish(T message);

    /**
     * Adds a message listener to the topic.
     *
     * @param messageConsumer the consumer that will handle incoming messages
     * @return a unique identifier for the listener, which can be used to remove it later
     */
    UUID addMessageListener(Consumer<T> messageConsumer);
}
