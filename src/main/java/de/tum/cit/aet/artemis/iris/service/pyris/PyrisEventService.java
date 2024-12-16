package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.service.pyris.event.CompetencyJolSetEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Service for handling Pyris events.
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
     * Handles a CompetencyJolSetEvent. The event is passed to the {@link de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService} to handle the judgement of
     * learning set.
     *
     * @see IrisCourseChatSessionService#onJudgementOfLearningSet
     * @param event the {@link CompetencyJolSetEvent} to handle
     */
    @EventListener
    public void handleCompetencyJolSetEvent(CompetencyJolSetEvent event) {
        log.debug("Processing CompetencyJolSetEvent");
        try {
            irisCourseChatSessionService.onJudgementOfLearningSet(event.getCompetencyJol());
            log.debug("Successfully processed CompetencyJolSetEvent");
        }
        catch (Exception e) {
            log.error("Failed to process CompetencyJolSetEvent: {}", event, e);
            throw e;
        }
    }

    /**
     * Handles a NewResultEvent. A new result represents a new submission result.
     * Depending on whether there was a build failure or not, the result is passed to the appropriate handler method inside the
     * {@link de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService}.
     *
     * @see IrisExerciseChatSessionService#onBuildFailure
     * @see IrisExerciseChatSessionService#onNewResult
     * @param event the {@link NewResultEvent} to handle
     */
    @EventListener
    public void handleNewResultEvent(NewResultEvent event) {
        log.debug("Processing NewResultEvent");
        try {
            var result = event.getResult();
            var submission = result.getSubmission();

            if (submission instanceof ProgrammingSubmission programmingSubmission) {
                if (programmingSubmission.isBuildFailed()) {
                    irisExerciseChatSessionService.onBuildFailure(result);
                }
                else {
                    irisExerciseChatSessionService.onNewResult(result);
                }
            }
            log.debug("Successfully processed NewResultEvent");
        }
        catch (Exception e) {
            log.error("Failed to process NewResultEvent: {}", event, e);
            throw e;
        }
    }
}
