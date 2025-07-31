package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import java.util.UUID;
import java.util.function.Consumer;

import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

public class RedissonDistributedTopic<T> implements DistributedTopic<T> {

    private final RTopic topic;

    public RedissonDistributedTopic(RTopic topic) {
        this.topic = topic;
    }

    @Override
    public void publish(T message) {
        topic.publish(message);
    }

    @Override
    public UUID addMessageListener(Consumer<T> messageConsumer) {
        topic.addListener(Object.class, (MessageListener<T>) (channel, msg) -> {
            messageConsumer.accept(msg);
        });
        return UUID.randomUUID();
    }
}
