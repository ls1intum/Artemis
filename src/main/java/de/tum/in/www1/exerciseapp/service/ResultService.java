package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.QuizSubmission;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Created by Josias Montag on 06.10.16.
 */
@Service
@Transactional
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
        continuousIntegrationService.get().onBuildCompleted(participation);
        // notify user via websocket
        messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", true);
        // handles new results and sends them to LTI consumers
        ltiService.onNewBuildResult(participation);
    }

    @Transactional(readOnly = true)
    public Result findFirstByParticipationIdOrderByCompletionDateDescEager(Long participationId) {
        Result result = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participationId).orElse(null);
        if (result != null) {
            result.getSubmission().getId();
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Result findFirstByParticipationIdAndRatedOrderByCompletionDateDescEager(Long participationId, boolean rated) {
        Result result = resultRepository.findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(participationId, rated).orElse(null);
        if (result != null) {
            result.getSubmission().getId();
        }
        return result;
    }
}
