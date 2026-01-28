package de.tum.cit.aet.artemis.iris.service.pyris;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.event.CompetencyJolSetEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.PyrisEvent;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;

/**
 * Service to handle Pyris events.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisEventService {

    private static final Logger log = LoggerFactory.getLogger(PyrisEventService.class);

    private final IrisCourseChatSessionService irisCourseChatSessionService;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    public PyrisEventService(IrisCourseChatSessionService irisCourseChatSessionService, IrisExerciseChatSessionService irisExerciseChatSessionService) {
        this.irisCourseChatSessionService = irisCourseChatSessionService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
    }

    /**
     * Triggers a Pyris action based on the received {@link PyrisEvent}.
     * This method processes the event and delegates the handling to the appropriate service.
     * <p>
     * Note: It's possible that no action is triggered if the event does not fulfill all requirements.
     * See {@link IrisCourseChatSessionService#handleCompetencyJolSetEvent(CompetencyJolSetEvent)} and
     * {@link IrisExerciseChatSessionService#handleNewResultEvent(NewResultEvent)} for more details on the specific
     * actions taken for each event type.
     *
     * @param event The event object received to trigger the matching action
     * @throws UnsupportedPyrisEventException if the event is not supported
     *
     * @see PyrisEvent
     */
    @Async
    public void trigger(PyrisEvent<?> event) {
        log.debug("Starting to process event of type: {}", event.getClass().getSimpleName());
        try {
            switch (event) {
                case CompetencyJolSetEvent competencyJolSetEvent -> {
                    log.debug("Processing CompetencyJolSetEvent: {}", competencyJolSetEvent);
                    irisCourseChatSessionService.handleCompetencyJolSetEvent(competencyJolSetEvent);
                    log.debug("Successfully processed CompetencyJolSetEvent");
                }
                case NewResultEvent newResultEvent -> {
                    log.debug("Processing NewResultEvent: {}", newResultEvent);
                    irisExerciseChatSessionService.handleNewResultEvent(newResultEvent);
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
