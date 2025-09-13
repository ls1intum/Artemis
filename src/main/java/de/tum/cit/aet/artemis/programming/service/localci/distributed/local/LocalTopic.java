package de.tum.cit.aet.artemis.programming.service.localci.distributed.local;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

public class LocalTopic<T> implements DistributedTopic<T> {

    private final ConcurrentHashMap<UUID, Consumer<T>> listeners = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(LocalTopic.class);

    @Override
    public void publish(T message) {
        for (Consumer<T> listener : listeners.values()) {
            try {
                listener.accept(message);
            }
            catch (Exception e) {
                log.error("Error while processing message in listener", e);
            }
        }
    }

    @Override
    public UUID addMessageListener(Consumer<T> messageConsumer) {
        UUID listenerId = UUID.randomUUID();
        listeners.put(listenerId, messageConsumer);
        return listenerId;
    }

    @Override
    public void removeMessageListener(UUID listenerId) {
        listeners.remove(listenerId);
    }
}
