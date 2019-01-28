package de.tum.in.www1.artemis.domain.scoring;

import de.tum.in.www1.artemis.domain.*;

/**
 * All or nothing means the full score is given if the answer is 100% correct,
 * otherwise a score of 0 is given
 */
public class ScoringStrategyMultipleChoiceAllOrNothing implements ScoringStrategy {
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        //check if the question is invalid: if true: -> return with full points
        if (question.isInvalid()) {
            return question.getScore();
        }

        if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer && question instanceof MultipleChoiceQuestion) {
            MultipleChoiceSubmittedAnswer mcAnswer = (MultipleChoiceSubmittedAnswer) submittedAnswer;
            MultipleChoiceQuestion mcQuestion = (MultipleChoiceQuestion) question;
            // iterate through each answer option and compare its correctness with the answer's selection
            for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                boolean isSelected = mcAnswer.isSelected(answerOption);
                //check if the answer is invalid: if true: -> ignore the answer in calculation
                if (!answerOption.isInvalid()) {
                    // if the user was wrong about this answer option, the entire answer can no longer be 100% correct
                    // being wrong means either a correct option is not selected, or an incorrect option is selected
                    if ((answerOption.isIsCorrect() && !isSelected) || (!answerOption.isIsCorrect() && isSelected)) {
                        return 0.0;
                    }
                }
            }
            // the user wasn't wrong about a single answer option => the answer is 100% correct
            return mcQuestion.getScore();
        }
        // the submitted answer's type doesn't fit the question's type => it cannot be correct
        return 0.0;
    }
}
