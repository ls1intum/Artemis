package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.QuizPointStatisticRepository;
import de.tum.in.www1.artemis.repository.QuizQuestionStatisticRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

@Service
public class QuizStatisticService {

    private final Logger log = LoggerFactory.getLogger(QuizStatisticService.class);

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final QuizPointStatisticRepository quizPointStatisticRepository;

    private final QuizQuestionStatisticRepository quizQuestionStatisticRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public QuizStatisticService(StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository, SimpMessageSendingOperations messagingTemplate,
            QuizPointStatisticRepository quizPointStatisticRepository, QuizQuestionStatisticRepository quizQuestionStatisticRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.quizPointStatisticRepository = quizPointStatisticRepository;
        this.quizQuestionStatisticRepository = quizQuestionStatisticRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 1. Go through all Results in the Participation and recalculate the score
     * 2. Recalculate the statistics of the given quizExercise
     *
     * @param quizExercise the changed QuizExercise object which will be used to recalculate the existing Results and Statistics
     */
    public void recalculateStatistics(QuizExercise quizExercise) {

        // reset all statistics
        if (quizExercise.getQuizPointStatistic() != null) {
            quizExercise.getQuizPointStatistic().resetStatistic();
        }
        else {
            var quizPointStatistic = new QuizPointStatistic();
            quizExercise.setQuizPointStatistic(quizPointStatistic);
            quizPointStatistic.setQuiz(quizExercise);
            quizExercise.recalculatePointCounters();
        }
        for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion.getQuizQuestionStatistic() != null) {
                quizQuestion.getQuizQuestionStatistic().resetStatistic();
            }
        }

        // add the Results in every participation of the given quizExercise to the statistics
        for (Participation participation : studentParticipationRepository.findByExerciseId(quizExercise.getId())) {

            Result latestRatedResult = null;
            Result latestUnratedResult = null;

            // update all Results of a participation
            for (Result result : resultRepository.findAllByParticipationIdOrderByCompletionDateDesc(participation.getId())) {

                // find latest rated Result
                if (Boolean.TRUE.equals(result.isRated()) && (latestRatedResult == null || latestRatedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestRatedResult = result;
                }
                // find latest unrated Result
                if (Boolean.FALSE.equals(result.isRated()) && (latestUnratedResult == null || latestUnratedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestUnratedResult = result;
                }
            }
            // update statistics with latest rated und unrated Result
            this.addResultToAllStatistics(quizExercise, latestRatedResult);
            this.addResultToAllStatistics(quizExercise, latestUnratedResult);
        }

        // save changed Statistics
        quizPointStatisticRepository.save(quizExercise.getQuizPointStatistic());
        quizPointStatisticRepository.flush();
        for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion.getQuizQuestionStatistic() != null) {
                quizQuestionStatisticRepository.save(quizQuestion.getQuizQuestionStatistic());
                quizQuestionStatisticRepository.flush();
            }
        }
    }

    /**
     * 1. check for each result if it's rated -> true: check if there is an old Result -> true: remove the old Result from the statistics 2. add new Result to the
     * quiz-point-statistic and all question-statistics
     *
     * @param results the results, which will be added to the statistics
     * @param quiz    the quizExercise with Questions where the results should contain to
     */
    public void updateStatistics(Set<Result> results, QuizExercise quiz) {

        if (results != null && quiz != null && quiz.getQuizQuestions() != null) {
            log.debug("update statistics with {} new results", results.size());

            for (Result result : results) {
                // check if the result is rated
                // NOTE: there is never an old Result if the new result is rated
                if (Boolean.FALSE.equals(result.isRated())) {
                    removeResultFromAllStatistics(quiz, getPreviousResult(result));
                }
                addResultToAllStatistics(quiz, result);
            }
            // save statistics
            quizPointStatisticRepository.save(quiz.getQuizPointStatistic());
            List<QuizQuestionStatistic> quizQuestionStatistics = new ArrayList<>();
            for (QuizQuestion quizQuestion : quiz.getQuizQuestions()) {
                if (quizQuestion.getQuizQuestionStatistic() != null) {
                    quizQuestionStatistics.add(quizQuestion.getQuizQuestionStatistic());
                }
            }
            quizQuestionStatisticRepository.saveAll(quizQuestionStatistics);
            // notify users via websocket about new results for the statistics.
            // filters out solution information
            quiz.filterForStatisticWebsocket();
            messagingTemplate.convertAndSend("/topic/statistic/" + quiz.getId(), quiz);
        }
    }

    /**
     * Go through all Results in the Participation and return the latest one before the new Result,
     *
     * @param newResult the new result object which will replace the old Result in the Statistics
     * @return the previous Result, which is presented in the Statistics (null if where is no previous Result)
     */
    private Result getPreviousResult(Result newResult) {
        Result oldResult = null;

        List<Result> allResultsForParticipation = resultRepository.findAllByParticipationIdOrderByCompletionDateDesc(newResult.getParticipation().getId());
        for (Result result : allResultsForParticipation) {
            // find the latest Result, which is presented in the Statistics
            if (result.isRated() == newResult.isRated() && result.getCompletionDate().isBefore(newResult.getCompletionDate()) && !result.equals(newResult)
                    && (oldResult == null || result.getCompletionDate().isAfter(oldResult.getCompletionDate()))) {
                oldResult = result;
            }
        }
        return oldResult;
    }

    /**
     * add Result to all Statistics of the given QuizExercise
     *
     * @param quizExercise contains the object of the quiz, where the Results will be added
     * @param result       the result which will be added (NOTE: add the submission to the result previously (this would improve the performance)
     */
    private void addResultToAllStatistics(QuizExercise quizExercise, Result result) {

        // update QuizPointStatistic with the result
        if (result != null) {
            // check if result contains a quizSubmission if true -> it's not necessary to fetch it from the database
            QuizSubmission quizSubmission = (QuizSubmission) result.getSubmission();
            quizExercise.getQuizPointStatistic().addResult(result.getScore(), result.isRated());
            for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
                // update QuestionStatistics with the result
                if (quizQuestion.getQuizQuestionStatistic() != null && quizSubmission != null) {
                    quizQuestion.getQuizQuestionStatistic().addResult(quizSubmission.getSubmittedAnswerForQuestion(quizQuestion), result.isRated());
                }
            }
        }
    }

    /**
     * remove Result from all Statistics of the given QuizExercise
     *
     * @param quizExercise contains the object of the quiz, where the Results will be removed
     * @param result       the result which will be removed (NOTE: add the submission to the result previously (this would improve the performance)
     */
    private void removeResultFromAllStatistics(QuizExercise quizExercise, Result result) {
        // update QuizPointStatistic with the result
        if (result != null) {
            log.debug("remove result from all statistics {}", result);
            // check if result contains a quizSubmission if true -> a it's not necessary to fetch it from the database
            QuizSubmission quizSubmission = (QuizSubmission) result.getSubmission();
            quizExercise.getQuizPointStatistic().removeOldResult(result.getScore(), result.isRated());
            for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
                // update QuestionStatistics with the result
                if (quizQuestion.getQuizQuestionStatistic() != null) {
                    quizQuestion.getQuizQuestionStatistic().removeOldResult(quizSubmission.getSubmittedAnswerForQuestion(quizQuestion), result.isRated());
                }
            }
        }
    }

}
