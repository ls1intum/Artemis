package de.tum.in.www1.artemis.service.connectors.pyris.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.iris.session.IrisCourseChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenAlertException;

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
     * Sends an event to the Pyris service.
     * throws UnsupportedOperationException if the event is not supported
     * throws AccessForbiddenAlertException if the event is not allowed
     * throws Exception if an error occurs while handling the event
     *
     * @param event The event to send
     *
     */
    public void trigger(PyrisEvent event) {
        try {
            switch (event) {
                case CompetencyJolSetEvent competencyJolSetEvent -> {
                    log.info("Received CompetencyJolSetEvent: {}", competencyJolSetEvent);
                    competencyJolSetEvent.handleEvent(irisCourseChatSessionService);
                }
                case SubmissionFailedEvent submissionFailedEvent -> {
                    log.info("Received SubmissionFailedEvent: {}", submissionFailedEvent);
                    submissionFailedEvent.handleEvent(irisExerciseChatSessionService);
                }
                case SubmissionSuccessfulEvent submissionSuccessfulEvent -> {
                    log.info("Received SubmissionSuccessfulEvent: {}", submissionSuccessfulEvent);
                    submissionSuccessfulEvent.handleEvent(irisCourseChatSessionService);
                }
                default -> throw new UnsupportedOperationException("Unsupported event: " + event);
            }
        }
        catch (AccessForbiddenAlertException e) {
            log.error("Access forbidden: {}", e.getMessage());
        }
        catch (UnsupportedOperationException e) {
            log.error("Unsupported event: {}", event, e);
        }
        catch (Exception e) {
            log.error("Error while handling event: {}", event, e);
        }
    }
}
