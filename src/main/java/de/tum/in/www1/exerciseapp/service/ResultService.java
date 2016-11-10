package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Participation;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.inject.Inject;

/**
 * Created by Josias Montag on 06.10.16.
 */
@Service
public class ResultService {

    @Inject
    private ContinuousIntegrationService continuousIntegrationService;

    @Inject
    private LtiService ltiService;

    @Inject
    SimpMessageSendingOperations messagingTemplate;


    /**
     * Perform async operations after we were notified about new results.
     *
     * @param participation Participation for which a new build is available
     */
    @Async
    public void onResultNotified(Participation participation) {
        // fetches the new build result
        continuousIntegrationService.onBuildCompleted(participation);
        // notify user via websocket
        messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", true);
        // handles new results and sends them to LTI consumers
        ltiService.onNewBuildResult(participation);
    }

}
