package de.tum.cit.aet.artemis.iris.service.pyris.event;

import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;

public class PyrisJobExpiredEvent extends PyrisEvent<PyrisJob> {

    public PyrisJobExpiredEvent(PyrisJob eventObject) {
        super(eventObject);
    }
}
