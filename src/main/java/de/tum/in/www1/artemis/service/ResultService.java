package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.QuizSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.ResultRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Created by Josias Montag on 06.10.16.
 */
@Service
public class ResultService {

    private final ResultRepository resultRepository;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final LtiService ltiService;
    private final SimpMessageSendingOperations messagingTemplate;

    public ResultService(ResultRepository resultRepository, Optional<ContinuousIntegrationService> continuousIntegrationService, LtiService ltiService, SimpMessageSendingOperations messagingTemplate) {
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
    public void onResultNotified(Participation participation) {
        // fetches the new build result
        Result result = continuousIntegrationService.get().onBuildCompleted(participation);
        if (result != null) {
            // notify user via websocket
            // TODO: send the result directly to the client to save 1 REST call and DB access
            messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", true);
            // handles new results and sends them to LTI consumers
            ltiService.onNewBuildResult(participation);
        }
    }
}
