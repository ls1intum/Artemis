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
