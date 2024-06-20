package de.tum.in.www1.artemis.service.connectors.pyris.event;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;

@Service
@Profile("iris")
public class PyrisEventService {

    private final IrisCourseChatSessionService irisCourseChatSessionService;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    public PyrisEventService(IrisCourseChatSessionService irisCourseChatSessionService, IrisExerciseChatSessionService irisExerciseChatSessionService) {
        this.irisCourseChatSessionService = irisCourseChatSessionService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
    }

    /**
     * Sends an event to the Pyris service.
     *
     * @param event The event to send
     */
    public void trigger(PyrisEvent event) {
        switch (event) {
            case CompetencyJolEvent competencyJolEvent -> {
                competencyJolEvent.handleEvent(irisCourseChatSessionService);
            }
            default -> throw new UnsupportedOperationException("Unsupported event: " + event);
        }
    }
}
