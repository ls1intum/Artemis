package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import de.tum.in.www1.exerciseapp.repository.*;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Moritz Issig on 22.11.17.
 */
@Service
public class StatisticService {

    private static Set<Long> semaphorSetUpdateStatistic = new HashSet<Long>();

    private final SimpMessageSendingOperations messagingTemplate;
    private final ParticipationRepository participationRepository;
    private final ResultRepository resultRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final QuizPointStatisticRepository quizPointStatisticRepository;
    private final QuestionStatisticRepository questionStatisticRepository;

    public StatisticService(SimpMessageSendingOperations messagingTemplate,
                            ParticipationRepository participationRepository,
                            ResultRepository resultRepository,
                            QuizSubmissionRepository quizSubmissionRepository,
                            QuizPointStatisticRepository quizPointStatisticRepository,
                            QuestionStatisticRepository questionStatisticRepository) {
        this.messagingTemplate = messagingTemplate;
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.quizPointStatisticRepository = quizPointStatisticRepository;
        this.questionStatisticRepository = questionStatisticRepository;
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
        if(quizExercise.getDueDate().isAfter(ZonedDateTime.now()) && quizExercise.getDueDate().isBefore(ZonedDateTime.now().plusSeconds(10))) {
            // semaphore, which checks if the service is still waiting for new Results for the given qiuzExercise
            if(!semaphorSetUpdateStatistic.contains(quizExercise.getId())) {
                semaphorSetUpdateStatistic.add(quizExercise.getId());
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        messagingTemplate.convertAndSend("/topic/statistic/" + quizExercise.getId(), true);
                        semaphorSetUpdateStatistic.remove(quizExercise.getId());
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

    /**
     * 1. Go through all Results in the Participation and recalculate the score
     * 2. recalculate the statistics of the given quizExercise
     *
     * @param quizExercise the changed QuizExercise object which will be used to recalculate the existing Results and Statistics
     */
    public void updateStatisticsAndResults(QuizExercise quizExercise){

        //reset all statistics
        quizExercise.getQuizPointStatistic().resetStatistic();
        for (Question question : quizExercise.getQuestions()) {
            question.getQuestionStatistic().resetStatistic();
        }

        // update the Results in every participation of the given quizExercise
        for (Participation participation : participationRepository.findByExerciseId(quizExercise.getId())) {

            Result latestRatedResult = null;
            Result latestUnratedResult = null;

            // update all Results of a participation
            for (Result result : resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId())) {

                QuizSubmission quizSubmission = (QuizSubmission) result.getSubmission();
                //recalculate existing score
                quizSubmission.calculateAndUpdateScores(quizExercise);
                //update Successful-Flag in Result
                result.setScore(Math.round(quizSubmission.getScoreInPoints() / quizExercise.getMaxTotalScore() * 100));

                // save the updated Result and its Submission
                resultRepository.save(result);
                quizSubmissionRepository.save(quizSubmission);

                // find latest rated Result
                if (result.getCompletionDate().isBefore(quizExercise.getDueDate())
                    && (latestRatedResult == null || latestRatedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestRatedResult = result;
                }
                // find latest unrated Result
                if (result.getCompletionDate().isAfter(quizExercise.getDueDate())
                    && (latestUnratedResult == null || latestUnratedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestUnratedResult = result;
                }
            }
            // update statistics with latest rated und unrated Result
            this.addResultToAllStatistics(quizExercise, latestRatedResult, true);
            this.addResultToAllStatistics(quizExercise, latestUnratedResult, false);

        }
        //save changed Statistics
        quizPointStatisticRepository.save(quizExercise.getQuizPointStatistic());
        for (Question question : quizExercise.getQuestions()) {
            questionStatisticRepository.save(question.getQuestionStatistic());
        }
    }

    /**
     * add Result to all Statistics of the given QuizExercise
     *
     * @param quizExercise contains the object of the quiz, where the Results will be added
     * @param result the result which will be added
     * @param rated defines if the given Result is rated or unrated
     */
    private void addResultToAllStatistics(QuizExercise quizExercise, Result result, boolean rated) {
        // update QuizPointStatistic with the result
        if (result != null) {
            quizExercise.getQuizPointStatistic().addResult(result.getScore(), rated);
        }
        for (Question question : quizExercise.getQuestions()) {
            // update QuestionStatistics with the result
            if (result != null) {
                question.getQuestionStatistic().addResult(((QuizSubmission) result.getSubmission()).getSubmittedAnswerForQuestion(question), rated);
            }
        }
    }

}
