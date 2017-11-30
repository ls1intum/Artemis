package de.tum.in.www1.exerciseapp.domain;

public class ScoringStrategyMultipleChoiceAllOrNothing implements ScoringStrategy{
    // All or nothing means we get the full score if the answer is 100% correct, and 0 points otherwise
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer && question instanceof MultipleChoiceQuestion) {
            MultipleChoiceSubmittedAnswer mcAnswer = (MultipleChoiceSubmittedAnswer) submittedAnswer;
            MultipleChoiceQuestion mcQuestion = (MultipleChoiceQuestion) question;
            // iterate through each answer option and compare its correctness with the answer's selection
            for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                boolean isSelected = mcAnswer.isSelected(answerOption);
                // if the user was wrong about this answer option, the entire answer can no longer be 100% correct
                // being wrong means either a correct option is not selected, or an incorrect option is selected
                if ((answerOption.isIsCorrect() && !isSelected) || (!answerOption.isIsCorrect() && isSelected)) {
                    return 0.0;
                }
            }
            // the user wasn't wrong about a single answer option => the answer is 100% correct
            return mcQuestion.getScore();
        } else {
            // the submitted answer's type doesn't fit the question's type => it cannot be correct
            return 0.0;
        }
    }
}
