package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.QuizExercise;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Josias Montag on 06.10.16.
 */
@Service
public class StatisticService {

    private static boolean semaphorUpdateStatistic = false;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final LtiService ltiService;
    private final SimpMessageSendingOperations messagingTemplate;

    public StatisticService(Optional<ContinuousIntegrationService> continuousIntegrationService, LtiService ltiService, SimpMessageSendingOperations messagingTemplate) {
        this.continuousIntegrationService = continuousIntegrationService;
        this.ltiService = ltiService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Perform async operations after we were notified about new results.
     *
     */
    @Async

    public void updateStatistic(QuizExercise quizExercise) {
        // notify user via websocket
        if(!semaphorUpdateStatistic){
            semaphorUpdateStatistic = true;
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    messagingTemplate.convertAndSend("/topic/statistic/"+quizExercise.getId(), true);
                    semaphorUpdateStatistic = false;
                }
            }, 500);
        }
    }

}
