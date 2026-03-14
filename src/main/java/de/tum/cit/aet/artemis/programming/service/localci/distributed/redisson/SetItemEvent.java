package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonCreator
    public SetItemEvent(@JsonProperty("eventType") EventType eventType, @JsonProperty("item") E item) {
        this.item = item;
        this.eventType = eventType;
    }

    public static <E> SetItemEvent<E> added(E item) {
        return new SetItemEvent<>(EventType.ADD, item);
    }

    public static <E> SetItemEvent<E> removed(E item) {
        return new SetItemEvent<>(EventType.REMOVE, item);
    }

    public EventType getEventType() {
        return eventType;
    }

    public E getItem() {
        return item;
    }
}
