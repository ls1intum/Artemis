package de.tum.cit.aet.artemis.domain.quiz.scoring;

import de.tum.cit.aet.artemis.domain.quiz.AnswerOption;
import de.tum.cit.aet.artemis.domain.quiz.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.domain.quiz.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.domain.quiz.QuizQuestion;
import de.tum.cit.aet.artemis.domain.quiz.SubmittedAnswer;

/**
 * Proportional with penalty means that every correctly selected/unselected answer increases the score by x and every incorrectly selected/unselected answer the score by x where x
 * = maxScore / numberOfAnswerOptions if the result is negative, a score of 0 is given instead
 */
public class ScoringStrategyMultipleChoiceProportionalWithPenalty implements ScoringStrategy {

    @Override
    public double calculateScore(QuizQuestion quizQuestion, SubmittedAnswer submittedAnswer) {
        // check if the quizQuestion is invalid: if true: -> return with full points
        if (quizQuestion.isInvalid()) {
            return quizQuestion.getPoints();
        }

        if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer mcAnswer && quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
            double totalOptions = mcQuestion.getAnswerOptions().size();
            double correctSelections = 0;
            double incorrectSelections = 0;

            // iterate through each answer option and count the correctly selected and incorrectly selected options
            for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                boolean isSelected = mcAnswer.isSelected(answerOption);
                // correct selection means either a correct option was selected or an incorrect option was not selected
                // invalid answer options are treated as if they were answered correctly
                if (answerOption.isInvalid() || (answerOption.isIsCorrect() && isSelected) || (!answerOption.isIsCorrect() && !isSelected)) {
                    correctSelections++;
                }
                else {
                    incorrectSelections++;
                }
            }
            // calculate the fraction of the total score this submission should get
            // every correct selection increases fraction by 1/totalOptions,
            // every incorrect selection decreases fraction by 1/totalOptions
            double fraction = ((correctSelections / totalOptions) - (incorrectSelections / totalOptions));

            // end result is maxScore * fraction, but at least 0
            return Math.max(0, quizQuestion.getPoints() * fraction);
        }
        // the submitted answer's type doesn't fit the quizQuestion's type => it cannot be correct
        return 0.0;
    }
}
