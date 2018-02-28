package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Moritz Issig on 22.11.17.
 */
@Service
public class StatisticService {

    private final Logger log = LoggerFactory.getLogger(StatisticService.class);


    private static ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

    private static Map<Long, List<Result>> resultToAdd = new ConcurrentHashMap<>();
    private static Map<Long, List<Result>> resultToRemove = new ConcurrentHashMap<>();

    private static StatisticService statisticService;

    @PostConstruct
    private void initStaticStatisticService() {
        statisticService = this;
    }

    static {
        threadPoolTaskScheduler.setThreadNamePrefix("StatisticUpdateScheduler");
        threadPoolTaskScheduler.initialize();

        threadPoolTaskScheduler.scheduleWithFixedDelay(() -> {
            if (!resultToAdd.isEmpty() && !resultToRemove.isEmpty()) {

                for(long quizId: resultToAdd.keySet()) {
                    QuizExercise quiz = statisticService.quizExerciseService.findOneWithQuestionsAndStatistics(quizId);
                     for(Result result: resultToAdd.get(quizId)) {
                         statisticService.addResultToAllStatistics(quiz, result);
                     }
                     for(Result result: resultToRemove.get(quizId)) {
                         statisticService.removeResultFromAllStatistics(quiz, result);
                     }
                    statisticService.quizPointStatisticRepository.save(quiz.getQuizPointStatistic());
                    for (Question question : quiz.getQuestions()) {
                        if (question.getQuestionStatistic() != null) {
                            statisticService.questionStatisticRepository.save(question.getQuestionStatistic());
                        }
                    }
                    //notify users via websocket about new results for the statistics.
                    statisticService.messagingTemplate.convertAndSend("/topic/statistic/" + quiz.getId(), true);
                }
                resultToAdd.clear();
                resultToRemove.clear();
            }
        }, 3000);
    }

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

                QuizSubmission quizSubmission = quizSubmissionRepository.findOne(result.getSubmission().getId());
                //recalculate existing score
                quizSubmission.calculateAndUpdateScores(quizExercise);
                //update Successful-Flag in Result
                result.setScore(Math.round(quizSubmission.getScoreInPoints() / quizExercise.getMaxTotalScore() * 100));

                // save the updated Result and its Submission
                resultRepository.save(result);
                quizSubmissionRepository.save(quizSubmission);

                // replace proxy with submission, because of Lazy-fetching
                result.setSubmission(quizSubmission);

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
    public void updateStatistics(Result newResult, Result oldResult, QuizExercise quiz) {

        if(oldResult != null){
            if(!resultToRemove.keySet().contains(quiz.getId())){
                resultToRemove.put(quiz.getId(), new ArrayList<>());
            }
            resultToRemove.get(quiz.getId()).add(oldResult);
        }
        if(!resultToAdd.keySet().contains(quiz.getId())){
            resultToAdd.put(quiz.getId(), new ArrayList<>());
        }
        resultToAdd.get(quiz.getId()).add(oldResult);
    }

    /**
     * add Result to all Statistics of the given QuizExercise
     *
     * @param quizExercise contains the object of the quiz, where the Results will be added
     * @param result the result which will be added (NOTE: add the submission to the result previously (this would improve the performance)
     */
    private void addResultToAllStatistics(QuizExercise quizExercise, Result result) {
        // update QuizPointStatistic with the result
        if (result != null) {
            // check if result contains a quizSubmission if true -> a it's not necessary to fetch it from the database
            QuizSubmission quizSubmission;
            if(result.getSubmission() instanceof QuizSubmission){
                quizSubmission = (QuizSubmission) result.getSubmission();
            } else {
                quizSubmission = quizSubmissionRepository.findOne(result.getSubmission().getId());
            }
            quizExercise.getQuizPointStatistic().addResult(result.getScore(), result.isRated());
            for (Question question : quizExercise.getQuestions()) {
                // update QuestionStatistics with the result
                if (question.getQuestionStatistic() != null && result.getSubmission() instanceof QuizSubmission) {
                    question.getQuestionStatistic().addResult(quizSubmission.getSubmittedAnswerForQuestion(question), result.isRated());
                }
            }
        }
    }

    /**
     * remove Result from all Statistics of the given QuizExercise
     *
     * @param quizExercise contains the object of the quiz, where the Results will be removed
     * @param result the result which will be removed (NOTE: add the submission to the result previously (this would improve the performance)
     */
    private void removeResultFromAllStatistics(QuizExercise quizExercise, Result result) {
        // update QuizPointStatistic with the result
        if (result != null) {
            // check if result contains a quizSubmission if true -> a it's not necessary to fetch it from the database
            QuizSubmission quizSubmission;
            if(result.getSubmission() instanceof QuizSubmission){
                quizSubmission = (QuizSubmission) result.getSubmission();
            } else {
                quizSubmission = quizSubmissionRepository.findOne(result.getSubmission().getId());
            }
            quizExercise.getQuizPointStatistic().removeOldResult(result.getScore(), result.isRated());
            for (Question question : quizExercise.getQuestions()) {
                // update QuestionStatistics with the result
                if (question.getQuestionStatistic() != null) {
                    question.getQuestionStatistic().removeOldResult(quizSubmission.getSubmittedAnswerForQuestion(question), result.isRated());
                }
            }
        }
    }

}
