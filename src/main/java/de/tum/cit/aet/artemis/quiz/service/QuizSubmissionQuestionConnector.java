package de.tum.cit.aet.artemis.quiz.service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;

/**
 * Reconnects saved quiz submission answers to an already loaded quiz question graph.
 */
public final class QuizSubmissionQuestionConnector {

    private QuizSubmissionQuestionConnector() {
        // Utility class
    }

    /**
     * Replaces submitted answer question references with the matching question instances from {@code quizExercise}.
     * This keeps filtering code on the graph whose lazy child collections were explicitly initialized.
     * Does nothing if the exercise's question collection is not initialized.
     *
     * @param quizSubmission the saved quiz submission whose answers should point to loaded questions
     * @param quizExercise   the quiz exercise loaded with its initialized question child collections
     */
    public static void reconnectSubmittedAnswersToLoadedQuestions(QuizSubmission quizSubmission, QuizExercise quizExercise) {
        if (quizExercise.getQuizQuestions() == null || !Hibernate.isInitialized(quizExercise.getQuizQuestions())) {
            return;
        }
        reconnectSubmittedAnswersToLoadedQuestions(quizSubmission, quizExercise.getQuizQuestions());
    }

    /**
     * Replaces submitted answer question references with matching instances from an explicitly loaded question collection.
     *
     * @param quizSubmission  the saved quiz submission whose answers should point to loaded questions
     * @param loadedQuestions the quiz questions with initialized child collections
     */
    public static void reconnectSubmittedAnswersToLoadedQuestions(QuizSubmission quizSubmission, Collection<QuizQuestion> loadedQuestions) {
        if (quizSubmission.getSubmittedAnswers() == null) {
            return;
        }
        Map<Long, QuizQuestion> loadedQuestionsById = loadedQuestions.stream().collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            QuizQuestion submittedAnswerQuestion = submittedAnswer.getQuizQuestion();
            if (submittedAnswerQuestion == null) {
                continue;
            }
            QuizQuestion loadedQuestion = loadedQuestionsById.get(submittedAnswerQuestion.getId());
            if (loadedQuestion != null) {
                submittedAnswer.setQuizQuestion(loadedQuestion);
            }
        }
    }
}
