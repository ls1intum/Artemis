package de.tum.in.www1.artemis.service.connectors.pyris.event;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;

public class SubmissionSuccessfulEvent extends PyrisEvent<IrisCourseChatSessionService, Result> {

    private final Result event;

    public SubmissionSuccessfulEvent(Result event) {
        this.event = event;
    }

    @Override
    public void handleEvent(IrisCourseChatSessionService service) {
        service.onSubmissionSuccess(event);
    }
}
