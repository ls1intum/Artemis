package de.tum.in.www1.artemis.service.connectors.pyris.event;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;

public class SubmissionFailedEvent extends PyrisEvent<IrisExerciseChatSessionService, Result> {

    private final Result event;

    public SubmissionFailedEvent(Result event) {
        this.event = event;
    }

    @Override
    public void handleEvent(IrisExerciseChatSessionService service) {
        service.onSubmissionFailure(event);
    }
}
