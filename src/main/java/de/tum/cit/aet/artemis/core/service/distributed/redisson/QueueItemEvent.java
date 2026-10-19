package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.io.Serial;
import java.io.Serializable;

/**
 * The queue mutation broadcast on a queue's notification topic, so that listeners on other nodes see it.
 *
 * <p>
 * Serializable because it travels over the wire like every other value in the distributed store. It carries the item,
 * so that has to be serializable too - which it already is, since Hazelcast requires it of everything queued.
 */
public class QueueItemEvent<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
