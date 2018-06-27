package de.tum.in.www1.artemis.domain;

public class ScoringStrategyAllOrNothing implements ScoringStrategy{
    // All or nothing means we get the full score if the answer is 100% correct, and 0 points otherwise
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        if (question.isAnswerCorrect(submittedAnswer)) {
            // the answer is 100% correct, so we can return the full score for this question
            return question.getScore();
        } else {
            // the answer is not 100% correct, so we return 0 points
            return 0.0;
        }
    }
}
