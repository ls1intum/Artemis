package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

/**
 * Event class for Redisson set notifications, used for publishing set changes via RTopic.
 *
 * @param <E> the type of items in the set
 */
public class SetItemEvent<E> {

    public enum EventType {
        ADD, REMOVE
    }

    private final E item;

    private final EventType eventType;

    private SetItemEvent(EventType eventType, E item) {
        this.item = item;
        this.eventType = eventType;
    }

    public static <E> SetItemEvent<E> added(E item) {
        return new SetItemEvent<>(EventType.ADD, item);
    }

    public static <E> SetItemEvent<E> removed(E item) {
        return new SetItemEvent<>(EventType.REMOVE, item);
    }

    public EventType getType() {
        return eventType;
    }

    public E getItem() {
        return item;
    }
}
