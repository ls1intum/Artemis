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

    private final SimpMessageSendingOperations messagingTemplate;

    public StatisticService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Perform async operations after we were notified about new results for the statistics.
     *
     * @param quizExercise contains the object of the quiz, for which statistics new result are available;
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
    /**
     * Perform async operations if the release state of an statistic is changed
     *
     * @param quizExercise contains the object of the quiz, for which statistics has been released or revoked;
     * @param payload: release = true , revoke = false.
     */
    @Async

    public void releaseStatistic(QuizExercise quizExercise, boolean payload) {
        // notify user via websocket
        // release: payload = true , revoke: payload = false.
        messagingTemplate.convertAndSend("/topic/statistic/" + quizExercise.getId() +"/release", payload);
    }

}
