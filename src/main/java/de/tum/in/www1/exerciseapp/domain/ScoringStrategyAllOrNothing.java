package de.tum.in.www1.exerciseapp.domain;

public class ScoringStrategyAllOrNothing implements ScoringStrategy{
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        if (question.isAnswerPerfect(submittedAnswer)) {
            return question.getScore();
        } else {
            return 0.0;
        }
    }
}
