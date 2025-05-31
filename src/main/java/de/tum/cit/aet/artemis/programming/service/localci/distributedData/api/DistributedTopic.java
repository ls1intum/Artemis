package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api;

import java.util.UUID;
import java.util.function.Consumer;

public interface DistributedTopic<T> {

    void publish(T message);

    UUID addMessageListener(Consumer<T> messageConsumer);
}
