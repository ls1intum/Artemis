package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

public class QueueItemEvent<T> {

    public enum EventType {
        ADD, REMOVE
    }

    private final T item;

    private final EventType eventType;

    private QueueItemEvent(EventType eventType, T item) {
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
