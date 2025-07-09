package de.tum.cit.aet.artemis.iris.service.pyris.event;

public abstract class PyrisEvent<T> {

    private final T eventObject;

    protected PyrisEvent(T eventObject) {
        if (eventObject == null) {
            throw new IllegalArgumentException("Event object cannot be null");
        }
        this.eventObject = eventObject;
    }

    public T getEventObject() {
        return eventObject;
    }
}
