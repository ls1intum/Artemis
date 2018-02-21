package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.*;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

/**
 * Created by Moritz Issig on 22.11.17.
 */
@Service
public class StatisticService {

    private static Set<Long> semaphorSetUpdateStatistic = new HashSet<Long>();
    private static Semaphore statisticSemaphore = new Semaphore(1);

    private final SimpMessageSendingOperations messagingTemplate;
    private final ParticipationRepository participationRepository;
    private final ResultRepository resultRepository;
    private final QuizExerciseService quizExerciseService;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final QuizPointStatisticRepository quizPointStatisticRepository;
    private final QuestionStatisticRepository questionStatisticRepository;

    public StatisticService(SimpMessageSendingOperations messagingTemplate,
                            ParticipationRepository participationRepository,
                            ResultRepository resultRepository,
                            QuizExerciseService quizExerciseService,
                            QuizSubmissionRepository quizSubmissionRepository,
                            QuizPointStatisticRepository quizPointStatisticRepository,
                            QuestionStatisticRepository questionStatisticRepository) {
        this.messagingTemplate = messagingTemplate;
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
        this.quizExerciseService = quizExerciseService;
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

    public void notifyStatisticWebsocket(QuizExercise quizExercise) {
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
    public void updateStatisticsAfterReEvaluation(QuizExercise quizExercise){

        //reset all statistics
        quizExercise.getQuizPointStatistic().resetStatistic();
        for (Question question : quizExercise.getQuestions()) {
            if (question.getQuestionStatistic() != null) {
                question.getQuestionStatistic().resetStatistic();
            }
        }

        // update the Results in every participation of the given quizExercise
        for (Participation participation : participationRepository.findByExerciseId(quizExercise.getId())) {

            Result latestRatedResult = null;
            Result latestUnratedResult = null;

            // update all Results of a participation
            for (Result result : resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId())) {

                // find latest rated Result
                if (result.isRated() && (latestRatedResult == null || latestRatedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestRatedResult = result;
                }
                // find latest unrated Result
                if (!result.isRated() && (latestUnratedResult == null || latestUnratedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestUnratedResult = result;
                }
            }
            // update statistics with latest rated und unrated Result
            this.addResultToAllStatistics(quizExercise, latestRatedResult);
            this.addResultToAllStatistics(quizExercise, latestUnratedResult);

        }
        //save changed Statistics
        quizPointStatisticRepository.save(quizExercise.getQuizPointStatistic());
        for (Question question : quizExercise.getQuestions()) {
            if (question.getQuestionStatistic() != null) {
                questionStatisticRepository.save(question.getQuestionStatistic());
            }
        }
    }

    /**
     * 1. lock critical part with semaphore for database transaction safety
     * 2. remove old Result from the quiz-point-statistic and all question-statistics
     * 3. add new Result to the quiz-point-statistic and all question-statistics
     * 4. save statistics
     * 5. notify statistic-websocket
     *
     * @param newResult the new Result, which will be added to the statistics
     * @param oldResult the old Result, which will be removedfrom the statistics. oldResult = null, if there is no old Result
     */
    public boolean updateStatistics(Result newResult, Result oldResult, QuizExercise quiz) {
        // critical part locked with Semaphore statisticSemaphore
        try {
            statisticSemaphore.acquire();
            // get quiz within semaphore to prevent lost updates
            // (if the same statistic is updated by several threads at the same time,
            // new values might be calculated based on outdated data)
            quiz = quizExerciseService.findOneWithQuestionsAndStatistics(quiz.getId());
            if (oldResult != null) {
                QuizSubmission quizSubmission = quizSubmissionRepository.findOne(oldResult.getSubmission().getId());

                for (Question question : quiz.getQuestions()) {
                    if (question.getQuestionStatistic() != null) {
                        // remove the previous Result from the QuestionStatistics
                        question.getQuestionStatistic().removeOldResult(quizSubmission.getSubmittedAnswerForQuestion(question), oldResult.isRated());
                    }
                }
                // add the new Result to the quizPointStatistic and remove the previous one
                quiz.getQuizPointStatistic().removeOldResult(oldResult.getScore(), oldResult.isRated());
            }
            addResultToAllStatistics(quiz, newResult);

            quizPointStatisticRepository.save(quiz.getQuizPointStatistic());
            for (Question question : quiz.getQuestions()) {
                if (question.getQuestionStatistic() != null) {
                    questionStatisticRepository.save(question.getQuestionStatistic());
                }
            }
        } catch (InterruptedException e) {
            return false;
        } finally {
            statisticSemaphore.release();
        }
        // notify statistics about new Result
        this.notifyStatisticWebsocket(quiz);
        return true;
    }

    /**
     * add Result to all Statistics of the given QuizExercise
     *
     * @param quizExercise contains the object of the quiz, where the Results will be added
     * @param result the result which will be added
     */
    private void addResultToAllStatistics(QuizExercise quizExercise, Result result) {

        // update QuizPointStatistic with the result
        if (result != null) {
            quizExercise.getQuizPointStatistic().addResult(result.getScore(), result.isRated());
            QuizSubmission quizSubmission = quizSubmissionRepository.findOne(result.getSubmission().getId());
            for (Question question : quizExercise.getQuestions()) {
                // update QuestionStatistics with the result
                if (question.getQuestionStatistic() != null) {
                    question.getQuestionStatistic().addResult(quizSubmission.getSubmittedAnswerForQuestion(question), result.isRated());
                }
            }
        }
    }

}
