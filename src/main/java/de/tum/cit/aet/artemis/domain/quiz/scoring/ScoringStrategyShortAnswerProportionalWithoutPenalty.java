package de.tum.cit.aet.artemis.domain.quiz.scoring;

import de.tum.cit.aet.artemis.domain.quiz.QuizQuestion;
import de.tum.cit.aet.artemis.domain.quiz.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.domain.quiz.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.domain.quiz.SubmittedAnswer;

/**
 * Proportional without penalty means that every correct mapping increases the score by maxScore / numberOfSpotsThatShouldHaveAMapping
 */
public class ScoringStrategyShortAnswerProportionalWithoutPenalty implements ScoringStrategy {

    @Override
    public double calculateScore(QuizQuestion quizQuestion, SubmittedAnswer submittedAnswer) {
        // check if the quizQuestion is invalid: if true: -> return with full points
        if (quizQuestion.isInvalid()) {
            return quizQuestion.getPoints();
        }

        if (submittedAnswer instanceof ShortAnswerSubmittedAnswer shortAnswerAnswer && quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
            double totalSolutionsCount = shortAnswerQuestion.getSpots().size();

            int[] values = ScoringStrategyShortAnswerUtil.getCorrectAndIncorrectSolutionCount(shortAnswerQuestion, shortAnswerAnswer);
            double correctSolutionsCount = values[0];

            // calculate the fraction of the total score the user should get
            // every correct mapping increases fraction by 1/mapped spots,
            double fraction = correctSolutionsCount / totalSolutionsCount;

            // end result is maxScore * fraction, but at least 0
            return Math.max(0, quizQuestion.getPoints() * fraction);
        }
        // the submitted answer's type doesn't fit the quizQuestion's type => it cannot be correct
        return 0.0;
    }
}
