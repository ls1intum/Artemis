package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisEventService;
import de.tum.cit.aet.artemis.iris.service.pyris.event.PyrisEvent;
import de.tum.cit.aet.artemis.iris.service.session.AbstractIrisChatSessionService;

@Profile(PROFILE_IRIS)
@Controller
public class PyrisEventApi extends AbstractIrisApi {

    private final PyrisEventService pyrisEventService;

    public PyrisEventApi(PyrisEventService pyrisEventService) {
        this.pyrisEventService = pyrisEventService;
    }

    public void trigger(PyrisEvent<? extends AbstractIrisChatSessionService<? extends IrisChatSession>, ?> event) {
        pyrisEventService.trigger(event);
    }
}
