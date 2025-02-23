package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.service.pyris.event.CompetencyJolSetEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.PyrisEvent;
import de.tum.cit.aet.artemis.iris.service.session.AbstractIrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;

/**
 * Service to handle Pyris events.
 */
@Service
@Profile(PROFILE_IRIS)
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
     * @throws UnsupportedPyrisEventException if the event is not supported
     *
     * @see PyrisEvent
     */
    @Async
    public void trigger(PyrisEvent<? extends AbstractIrisChatSessionService<? extends IrisChatSession>, ?> event) {
        log.debug("Starting to process event of type: {}", event.getClass().getSimpleName());
        try {
            switch (event) {
                case CompetencyJolSetEvent competencyJolSetEvent -> {
                    log.debug("Processing CompetencyJolSetEvent: {}", competencyJolSetEvent);
                    competencyJolSetEvent.handleEvent(irisCourseChatSessionService);
                    log.debug("Successfully processed CompetencyJolSetEvent");
                }
                case NewResultEvent newResultEvent -> {
                    log.debug("Processing NewResultEvent: {}", newResultEvent);
                    newResultEvent.handleEvent(irisExerciseChatSessionService);
                    log.debug("Successfully processed NewResultEvent");
                }
                default -> throw new UnsupportedPyrisEventException("Unsupported event type: " + event.getClass().getSimpleName());
            }
        }
        catch (Exception e) {
            log.error("Failed to process event: {}", event, e);
            throw e;
        }
    }
}
