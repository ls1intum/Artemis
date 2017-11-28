package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.QuizExercise;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
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
        // if the quiz-timer is ending this service waits for 300ms for additional Results before its sending the Websocket
        if(quizExercise.getDueDate().isAfter(ZonedDateTime.now()) && quizExercise.getDueDate().isBefore(ZonedDateTime.now().plusSeconds(10))){
            // semaphore, which checks if the service is still waiting for new Results
            if(!semaphorUpdateStatistic){
                semaphorUpdateStatistic = true;
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        messagingTemplate.convertAndSend("/topic/statistic/" + quizExercise.getId(), true);
                        semaphorUpdateStatistic = false;
                    }
                }, 300);
            }
        }
        // if the quiz is running or later if its over the websocket will be notified instantly
        else{
            messagingTemplate.convertAndSend("/topic/statistic/" + quizExercise.getId(), true);
        }

    }

}
