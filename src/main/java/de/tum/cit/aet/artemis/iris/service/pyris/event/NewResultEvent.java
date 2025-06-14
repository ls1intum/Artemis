package de.tum.cit.aet.artemis.iris.service.pyris.event;

import de.tum.cit.aet.artemis.assessment.domain.Result;

public class NewResultEvent extends PyrisEvent<Result> {

    public NewResultEvent(Result eventObject) {
        super(eventObject);
    }
}
