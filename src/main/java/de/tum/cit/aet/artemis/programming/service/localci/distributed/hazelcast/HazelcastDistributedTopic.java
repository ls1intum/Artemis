package de.tum.cit.aet.artemis.programming.service.localci.distributed.hazelcast;

import java.util.UUID;
import java.util.function.Consumer;

import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

public class HazelcastDistributedTopic<T> implements DistributedTopic<T> {

    private final ITopic<T> topic;

    public HazelcastDistributedTopic(ITopic<T> topic) {
        this.topic = topic;
    }

    @Override
    public void publish(T message) {
        topic.publish(message);
    }

    @Override
    public UUID addMessageListener(Consumer<T> messageConsumer) {
        return topic.addMessageListener(message -> {
            messageConsumer.accept(message.getMessageObject());
        });
    }

    @Override
    public void removeMessageListener(UUID listenerId) {
        topic.removeMessageListener(listenerId);
    }
}
