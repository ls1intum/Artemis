package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic;

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

    /**
     * Removes a message listener from the topic.
     *
     * @param listenerId the unique identifier of the listener to remove
     */
    void removeMessageListener(UUID listenerId);
}
