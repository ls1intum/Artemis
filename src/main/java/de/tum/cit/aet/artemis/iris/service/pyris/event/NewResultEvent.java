package de.tum.cit.aet.artemis.iris.service.pyris.event;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

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
