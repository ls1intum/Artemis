package de.tum.in.www1.exerciseapp.domain.scoring;

import de.tum.in.www1.exerciseapp.domain.*;

/**
 * Proportional with Penalty means that
 * every correctly selected/unselected answer increases the score by x and
 * every incorrectly selected/unselected answer the score by x
 * where x = maxScore / numberOfAnswerOptions
 * if the result is negative, a score of 0 is given instead
 */
public class ScoringStrategyMultipleChoiceProportionalWithPenalty implements ScoringStrategy {
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer && question instanceof MultipleChoiceQuestion) {
            MultipleChoiceSubmittedAnswer mcAnswer = (MultipleChoiceSubmittedAnswer) submittedAnswer;
            MultipleChoiceQuestion mcQuestion = (MultipleChoiceQuestion) question;

            double totalOptions = mcQuestion.getAnswerOptions().size();
            double correctSelections = 0;
            double incorrectSelections = 0;

            // iterate through each answer option and count the correctly selected and incorrectly selected options
            for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                boolean isSelected = mcAnswer.isSelected(answerOption);
                // correct selection means either a correct option was selected or an incorrect option was not selected
                if ((answerOption.isIsCorrect() && isSelected) || (!answerOption.isIsCorrect() && !isSelected)) {
                    correctSelections++;
                } else {
                    incorrectSelections++;
                }
            }
            // calculate the fraction of the total score this submission should get
            // every correct selection increases fraction by 1/totalOptions,
            // every incorrect selection decreases fraction by 1/totalOptions
            double fraction = ((correctSelections / totalOptions) - (incorrectSelections / totalOptions));

            // end result is maxScore * fraction, but at least 0
            return Math.max(0, question.getScore() * fraction);
        } else {
            // the submitted answer's type doesn't fit the question's type => it cannot be correct
            return 0.0;
        }
    }
}
