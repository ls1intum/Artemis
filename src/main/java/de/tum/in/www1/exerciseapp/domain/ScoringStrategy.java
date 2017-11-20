package de.tum.in.www1.exerciseapp.domain;

public interface ScoringStrategy {
    double calculateScore(Question question, SubmittedAnswer submittedAnswer);
}
