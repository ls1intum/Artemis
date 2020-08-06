package de.tum.in.www1.artemis.domain.quiz.scoring;

import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.domain.quiz.SubmittedAnswer;

public interface ScoringStrategy {

    /**
     * Calculate the score for the given answer to the given quizQuestion
     *
     * @param quizQuestion    the quizQuestion to score
     * @param submittedAnswer the answer to score
     * @return the resulting score (usually between 0.0 and quizQuestion.getScore())
     */
    double calculateScore(QuizQuestion quizQuestion, SubmittedAnswer submittedAnswer);
}
