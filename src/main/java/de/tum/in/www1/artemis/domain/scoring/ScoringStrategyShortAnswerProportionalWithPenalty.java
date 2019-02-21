package de.tum.in.www1.artemis.domain.scoring;

import de.tum.in.www1.artemis.domain.Question;
import de.tum.in.www1.artemis.domain.ShortAnswerQuestion;
import de.tum.in.www1.artemis.domain.ShortAnswerSubmittedAnswer;
import de.tum.in.www1.artemis.domain.SubmittedAnswer;

/**
 * Proportional with Penalty means that
 * every correct mapping increases the score by x and
 * every incorrect mapping decreases the score by x
 * where x = maxScore / numberOfSpotsThatShouldHaveAMapping
 * if the result is negative, a score of 0 is given instead
 */
public class ScoringStrategyShortAnswerProportionalWithPenalty implements ScoringStrategy{
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        // check if the question is invalid: if true: -> return with full points
        if (question.isInvalid()) {
            return question.getScore();
        }

        if (submittedAnswer instanceof ShortAnswerSubmittedAnswer && question instanceof ShortAnswerQuestion) {
            ShortAnswerSubmittedAnswer shortAnswerAnswer = (ShortAnswerSubmittedAnswer) submittedAnswer;
            ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) question;
            double totalSolutionsCount = shortAnswerQuestion.getSpots().size();

            int[] values = ScoringStrategyShortAnswerUtil.getCorrectAndIncorrectSolutionCount(shortAnswerQuestion,shortAnswerAnswer);
            double correctSolutionsCount = values[0];
            double incorrectSolutionsCount = values[1];

            // calculate the fraction of the total score the user should get
            // every correct mapping increases fraction by 1/mapped spots,
            // every incorrect mapping decreases fraction by 1/mapped spots
            double fraction = ((correctSolutionsCount / totalSolutionsCount) - (incorrectSolutionsCount / totalSolutionsCount));

            // end result is maxScore * fraction, but at least 0
            return Math.max(0, question.getScore() * fraction);
        }
        // the submitted answer's type doesn't fit the question's type => it cannot be correct
        return 0.0;
    }
}
