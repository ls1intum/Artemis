package de.tum.cit.aet.artemis.iris.service.pyris.event;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.service.session.AbstractIrisChatSessionService;

public abstract class PyrisEvent<S extends AbstractIrisChatSessionService<? extends IrisChatSession>, T> {

    /**
     * Handles the event using the given service.
     *
     * @param service The service to handle the event for
     */
    public abstract void handleEvent(S service);
}
