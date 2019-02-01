package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Created by Josias Montag on 06.10.16.
 */
@Service
public class ResultService {

    private final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final UserService userService;
    private final ParticipationService participationService;
    private final ResultRepository resultRepository;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final LtiService ltiService;
    private final SimpMessageSendingOperations messagingTemplate;


    public ResultService(UserService userService, ParticipationService participationService, ResultRepository resultRepository, Optional<ContinuousIntegrationService> continuousIntegrationService, LtiService ltiService, SimpMessageSendingOperations messagingTemplate) {
        this.userService = userService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.continuousIntegrationService = continuousIntegrationService;
        this.ltiService = ltiService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Perform async operations after we were notified about new results.
     *
     * @param participation Participation for which a new build is available
     */
    @Async
    @Deprecated
    public void onResultNotifiedOld(Participation participation) {
        log.debug("Received new build result for participation " + participation.getId());
        // fetches the new build result
        Result result = continuousIntegrationService.get().onBuildCompletedOld(participation);
        notifyUser(participation, result);
    }

    /**
     * Use the given requestBody to extract the relevant information from it
     * @param participation Participation for which the build was finished
     * @param requestBody RequestBody containing all relevant information
     */
    public void onResultNotifiedNew(Participation participation, Object requestBody) throws Exception {
        log.info("Received new build result (NEW) for participation " + participation.getId());

        Result result = continuousIntegrationService.get().onBuildCompletedNew(participation, requestBody);
        notifyUser(participation, result);
    }

    private void notifyUser(Participation participation, Result result) {
        if (result != null) {
            // notify user via websocket
            messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", result);

            //TODO: can we avoid to invoke this code for non LTI students? (to improve performance)
//            if (participation.isLti()) {
//            }
            // handles new results and sends them to LTI consumers
            ltiService.onNewBuildResult(participation);
        }
    }

    /**
     * Handle the manual creation of a new result potentially including feedback
     * @param result
     */
    public void createNewResult(Result result) {
        if(!result.getFeedbacks().isEmpty()) {
            result.setHasFeedback(true);
        }

        //TODO: in this case we do not have a submission. However, it would be good to create one, even if it might be "empty"
        User user = userService.getUserWithGroupsAndAuthorities();

        result.setAssessmentType(AssessmentType.MANUAL);
        result.setAssessor(user);

        //manual feedback is always rated
        result.setRated(true);

        result.getFeedbacks().forEach(feedback -> {
            feedback.setResult(result);
        });

        // this call should cascade all feedback relevant changed and save them accordingly
        Result savedResult = resultRepository.save(result);
        try {
            result.getParticipation().addResult(savedResult);
            participationService.save(result.getParticipation());
        } catch (NullPointerException ex) {
            log.warn("Unable to load result list for participation", ex);
        }

        messagingTemplate.convertAndSend("/topic/participation/" + result.getParticipation().getId() + "/newResults", result);
        ltiService.onNewBuildResult(savedResult.getParticipation());
    }
}
