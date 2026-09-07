package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.redisson.api.RReliableTopic;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;

/**
 * Topic backed by {@code RReliableTopic}, which persists messages in a Redis stream and tracks a per-subscriber
 * position.
 *
 * <p>
 * Plain Redis pub/sub is at-most-once: it drops messages for a subscriber that is momentarily disconnected or too slow
 * (Redis disconnects subscribers that exceed {@code client-output-buffer-limit pubsub}). For the scheduling topics that
 * would mean an exercise or quiz is silently never scheduled, so those use this implementation instead.
 */
public class RedissonReliableDistributedTopic<T> implements DistributedTopic<T> {

    private static final Logger log = LoggerFactory.getLogger(RedissonReliableDistributedTopic.class);

    private final RReliableTopic reliableTopic;

    /**
     * Redisson identifies reliable-topic listeners by String, so map them onto the UUID this API exposes.
     */
    private final Map<UUID, String> listenerRegistrations = new ConcurrentHashMap<>();

    public RedissonReliableDistributedTopic(RReliableTopic reliableTopic) {
        this.reliableTopic = reliableTopic;
    }

    @Override
    public void publish(T message) {
        reliableTopic.publish(message);
    }

    /**
     * Redisson needs a class token to pick a listener type. The messages here are arbitrary payloads decoded by the
     * configured codec, so the token is only used to satisfy the signature.
     *
     * @param <M> the message type the caller expects
     * @return {@code Object.class} widened to the caller's message type
     */
    @SuppressWarnings("unchecked")
    private static <M> Class<M> anyMessageType() {
        return (Class<M>) (Class<?>) Object.class;
    }

    @Override
    public UUID addMessageListener(Consumer<T> messageConsumer) {
        MessageListener<T> listener = (_, message) -> messageConsumer.accept(message);
        String registrationId = reliableTopic.addListener(anyMessageType(), listener);
        UUID listenerId = UUID.randomUUID();
        listenerRegistrations.put(listenerId, registrationId);
        return listenerId;
    }

    @Override
    public void removeMessageListener(UUID listenerId) {
        String registrationId = listenerRegistrations.remove(listenerId);
        if (registrationId == null) {
            log.warn("No reliable topic listener found for UUID: {}", listenerId);
            return;
        }
        reliableTopic.removeListener(registrationId);
    }
}
