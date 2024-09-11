package de.tum.cit.aet.artemis.domain.quiz.scoring;

import de.tum.cit.aet.artemis.domain.quiz.AnswerOption;
import de.tum.cit.aet.artemis.domain.quiz.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.domain.quiz.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.domain.quiz.QuizQuestion;
import de.tum.cit.aet.artemis.domain.quiz.SubmittedAnswer;

/**
 * All or nothing means the full score is given if the answer is 100% correct, otherwise a score of 0 is given
 */
public class ScoringStrategyMultipleChoiceAllOrNothing implements ScoringStrategy {

    @Override
    public double calculateScore(QuizQuestion quizQuestion, SubmittedAnswer submittedAnswer) {
        // check if the quizQuestion is invalid: if true: -> return with full points
        if (quizQuestion.isInvalid()) {
            return quizQuestion.getPoints();
        }

        if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer mcAnswer && quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
            // iterate through each answer option and compare its correctness with the answer's selection
            for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                boolean isSelected = mcAnswer.isSelected(answerOption);
                // check if the answer is invalid: if true: -> ignore the answer in calculation
                if (!answerOption.isInvalid()) {
                    // if the user was wrong about this answer option, the entire answer can no longer be 100% correct
                    // being wrong means either a correct option is not selected, or an incorrect option is selected
                    if ((answerOption.isIsCorrect() && !isSelected) || (!answerOption.isIsCorrect() && isSelected)) {
                        return 0.0;
                    }
                }
            }
            // the user wasn't wrong about a single answer option => the answer is 100% correct
            return mcQuestion.getPoints();
        }
        // the submitted answer's type doesn't fit the quizQuestion's type => it cannot be correct
        return 0.0;
    }
}
