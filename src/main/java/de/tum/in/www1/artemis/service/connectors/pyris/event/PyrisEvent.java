package de.tum.in.www1.artemis.service.connectors.pyris.event;

import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.service.iris.session.AbstractIrisChatSessionService;

public abstract class PyrisEvent<S extends AbstractIrisChatSessionService<? extends IrisChatSession>, T> {

    /**
     * Handles the event using the given service.
     *
     * @param service The service to handle the event for
     */
    public abstract void handleEvent(S service);
}
