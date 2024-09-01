package de.tum.in.www1.artemis.service.connectors.pyris.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.service.iris.session.AbstractIrisChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;

/**
 * Service to handle Pyris events.
 */
@Service
@Profile("iris")
public class PyrisEventService {

    private static final Logger log = LoggerFactory.getLogger(PyrisEventService.class);

    private final IrisCourseChatSessionService irisCourseChatSessionService;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    public PyrisEventService(IrisCourseChatSessionService irisCourseChatSessionService, IrisExerciseChatSessionService irisExerciseChatSessionService) {
        this.irisCourseChatSessionService = irisCourseChatSessionService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
    }

    /**
     * Triggers a Pyris pipeline based on the received {@link PyrisEvent}.
     *
     * @param event The event object received to trigger the matching pipeline
     * @throws UnsupportedOperationException if the event is not supported
     *
     * @see PyrisEvent
     */
    public void trigger(PyrisEvent<? extends AbstractIrisChatSessionService<? extends IrisChatSession>, ?> event) {
        switch (event) {
            case CompetencyJolSetEvent competencyJolSetEvent -> {
                log.info("Received CompetencyJolSetEvent: {}", competencyJolSetEvent);
                competencyJolSetEvent.handleEvent(irisCourseChatSessionService);
            }
            case NewResultEvent newResultEvent -> {
                log.info("Received NewResultEvent: {}", newResultEvent);
                newResultEvent.handleEvent(irisExerciseChatSessionService);
            }
            default -> throw new UnsupportedOperationException("Unsupported event: " + event);
        }
    }
}
