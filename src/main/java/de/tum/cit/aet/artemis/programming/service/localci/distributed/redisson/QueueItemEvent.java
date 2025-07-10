package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

public class QueueItemEvent<T> {

    public enum EventType {
        ADD, REMOVE
    }

    private final T item;

    private final EventType eventType;

    public QueueItemEvent(EventType eventType, T item) {
        this.eventType = eventType;
        this.item = item;
    }

    public EventType getType() {
        return eventType;
    }

    public T getItem() {
        return item;
    }

}
