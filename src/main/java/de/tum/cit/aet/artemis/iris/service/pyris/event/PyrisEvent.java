package de.tum.cit.aet.artemis.iris.service.pyris.event;

import java.util.Optional;

import org.springframework.context.ApplicationEvent;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Base class for Pyris events.
 */
public abstract class PyrisEvent extends ApplicationEvent {

    public PyrisEvent(Object source) {
        super(source);
    }

    /**
     * Returns the user associated with this event.
     *
     * @return the user
     */
    public abstract Optional<User> getUser();
}
