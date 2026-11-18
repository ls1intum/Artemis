package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import java.util.UUID;
import java.util.function.Consumer;

import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;

/**
 * Topic backed by a Hazelcast reliable topic, which stores messages in a ringbuffer so that a subscriber which is
 * briefly slow or reconnecting still receives them, unlike a plain {@code ITopic} where they are simply dropped.
 */
public class HazelcastReliableDistributedTopic<T> implements DistributedTopic<T> {

    private final ITopic<T> reliableTopic;

    public HazelcastReliableDistributedTopic(ITopic<T> reliableTopic) {
        this.reliableTopic = reliableTopic;
    }

    @Override
    public void publish(T message) {
        reliableTopic.publish(message);
    }

    @Override
    public UUID addMessageListener(Consumer<T> messageConsumer) {
        return reliableTopic.addMessageListener(message -> messageConsumer.accept(message.getMessageObject()));
    }

    @Override
    public void removeMessageListener(UUID listenerId) {
        reliableTopic.removeMessageListener(listenerId);
    }
}
