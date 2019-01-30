package de.tum.in.www1.artemis.domain.scoring;

import de.tum.in.www1.artemis.domain.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Proportional with Penalty means that
 * every correct mapping increases the score by x and
 * every incorrect mapping decreases the score by x
 * where x = maxScore / numberOfDropLocationsThatShouldHaveAMapping
 * if the result is negative, a score of 0 is given instead
 */
public class ScoringStrategyShortAnswerProportionalWithPenalty implements ScoringStrategy{
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        //check if the question is invalid: if true: -> return with full points
        if (question.isInvalid()) {
            return question.getScore();
        }

        if (submittedAnswer instanceof ShortAnswerSubmittedAnswer && question instanceof ShortAnswerQuestion) {
            ShortAnswerSubmittedAnswer saAnswer = (ShortAnswerSubmittedAnswer) submittedAnswer;
            ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) question;
            double totalSolutions = saQuestion.getSpots().size();

            double[] values = ScoringStrategyShortAnswerUtil.getCorrectAndIncorrectSolutionsrShortAnswerQuestion(saQuestion,saAnswer);
            double correctSolutions = values[0];
            double incorrectSolutions = values[1];

            // calculate the fraction of the total score the user should get
            // every correct mapping increases fraction by 1/mappedDropLocations,
            // every incorrect mapping decreases fraction by 1/mappedDropLocations
            double fraction = ((correctSolutions / totalSolutions) - (incorrectSolutions / totalSolutions));

            // end result is maxScore * fraction, but at least 0
            return Math.max(0, question.getScore() * fraction);
        }
        // the submitted answer's type doesn't fit the question's type => it cannot be correct
        return 0.0;
    }
}
