package de.tum.cit.aet.artemis.programming.service.localci.distributed.local;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

public class LocalTopic<T> implements DistributedTopic<T> {

    private final ConcurrentHashMap<UUID, Consumer<T>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(T message) {
        for (Consumer<T> listener : listeners.values()) {
            listener.accept(message);
        }

    }

    @Override
    public UUID addMessageListener(Consumer<T> messageConsumer) {
        UUID listenerId = UUID.randomUUID();
        listeners.put(listenerId, messageConsumer);
        return listenerId;
    }
}
