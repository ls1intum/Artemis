package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Created by Moritz Issig on 22.11.17.
 */
@Service
public class StatisticService {

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
     * 1. Go through all Results in the Participation and recalculate the score
     * 2. recalculate the statistics of the given quizExercise
     *
     * @param quizExercise the changed QuizExercise object which will be used to recalculate the existing Results and Statistics
     */
    public void recalculateStatistics(QuizExercise quizExercise) {

        //reset all statistics
        quizExercise.getQuizPointStatistic().resetStatistic();
        for (Question question : quizExercise.getQuestions()) {
            if (question.getQuestionStatistic() != null) {
                question.getQuestionStatistic().resetStatistic();
            }
        }

        // add the Results in every participation of the given quizExercise to the statistics
        for (Participation participation : participationRepository.findByExerciseId(quizExercise.getId())) {

            Result latestRatedResult = null;
            Result latestUnratedResult = null;

            // update all Results of a participation
            for (Result result : resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId())) {

                // find latest rated Result
                if (result.isRated() == Boolean.TRUE && (latestRatedResult == null || latestRatedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestRatedResult = result;
                }
                // find latest unrated Result
                if (result.isRated() == Boolean.FALSE && (latestUnratedResult == null || latestUnratedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
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
     * 1. check for each result if it's rated
     *      -> true: check if there is an old Result
     *          -> true: remove the old Result from the statistics
     * 2. add new Result to the quiz-point-statistic and all question-statistics
     *
     * @param results the results, which will be added to the statistics
     * @param quiz the quizExercise with Questions where the results should contain to
     */
    public void updateStatistics(Set<Result> results, QuizExercise quiz) {

        if (results != null && quiz != null && quiz.getQuestions() != null) {

            for (Result result : results) {
                // check if the result is rated
                // NOTE: where is never an old Result if the new result is rated
                if (result.isRated() == Boolean.FALSE) {
                    removeResultFromAllStatistics(quiz, getPreviousResult(result));
                }
                addResultToAllStatistics(quiz, result);
            }
            //save statistics
            quizPointStatisticRepository.save(quiz.getQuizPointStatistic());
            for (Question question : quiz.getQuestions()) {
                if (question.getQuestionStatistic() != null) {
                    questionStatisticRepository.save(question.getQuestionStatistic());
                }
            }
            //notify users via websocket about new results for the statistics.
            //filters out solution-Informations
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

        for (Result result : resultRepository.findByParticipationIdOrderByCompletionDateDesc(newResult.getParticipation().getId())) {
            //find the latest Result, which is presented in the Statistics
            if (result.isRated() == newResult.isRated()
                && result.getCompletionDate().isBefore(newResult.getCompletionDate())
                && !result.equals(newResult)
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
                quizSubmission = quizSubmissionRepository.findById(result.getSubmission().getId()).get();
            }
            quizExercise.getQuizPointStatistic().addResult(result.getScore(), result.isRated());
            for (Question question : quizExercise.getQuestions()) {
                // update QuestionStatistics with the result
                if (question.getQuestionStatistic() != null && quizSubmission instanceof QuizSubmission) {
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
                quizSubmission = quizSubmissionRepository.findById(result.getSubmission().getId()).get();
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
