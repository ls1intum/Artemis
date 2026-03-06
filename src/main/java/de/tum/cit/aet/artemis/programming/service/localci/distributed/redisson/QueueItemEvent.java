package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class QueueItemEvent<T> {

    public enum EventType {
        ADD, REMOVE
    }

    private final T item;

    private final EventType eventType;

    @JsonCreator
    private QueueItemEvent(@JsonProperty("eventType") EventType eventType, @JsonProperty("item") T item) {
        this.eventType = eventType;
        this.item = item;
    }

    public static <T> QueueItemEvent<T> added(T item) {
        return new QueueItemEvent<>(EventType.ADD, item);
    }

    public static <T> QueueItemEvent<T> removed(T item) {
        return new QueueItemEvent<>(EventType.REMOVE, item);
    }

    public EventType getType() {
        return eventType;
    }

    public T getItem() {
        return item;
    }

}
