package de.tum.in.www1.artemis.service.connectors.pyris.event;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;

public class NewResultEvent extends PyrisEvent<IrisExerciseChatSessionService, Result> {

    private final Result eventObject;

    public NewResultEvent(Result eventObject) {
        this.eventObject = eventObject;
    }

    @Override
    public void handleEvent(IrisExerciseChatSessionService service) {
        var submission = eventObject.getSubmission();
        // We only care about programming submissions
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            if (programmingSubmission.isBuildFailed()) {
                service.onBuildFailure(eventObject);
            }
            else {
                service.onNewResult(eventObject);
            }
        }
    }
}
