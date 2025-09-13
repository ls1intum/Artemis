package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.RedisConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

public class RedissonDistributedTopic<T> implements DistributedTopic<T> {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedTopic.class);

    private final RTopic topic;

    private final Map<UUID, Integer> listenerRegistrations = new ConcurrentHashMap<>();

    public RedissonDistributedTopic(RTopic topic) {
        this.topic = topic;
    }

    @Override
    public void publish(T message) {
        topic.publish(message);
    }

    @Override
    public UUID addMessageListener(Consumer<T> messageConsumer) {
        int listenerId = topic.addListener(Object.class, (MessageListener<T>) (channel, msg) -> {
            messageConsumer.accept(msg);
        });
        UUID uuid = UUID.randomUUID();
        listenerRegistrations.put(uuid, listenerId);
        return uuid;
    }

    @Override
    public void removeMessageListener(UUID uuid) {
        try {
            Integer listenerId = listenerRegistrations.get(uuid);
            if (listenerId == null) {
                log.warn("No listener found for UUID: {}", uuid);
                return;
            }
            topic.removeListener(listenerId);
            listenerRegistrations.remove(uuid);
        }
        catch (RedisConnectionException e) {
            log.error("Could not remove listener due to Redis connection exception.");
        }
    }
}
