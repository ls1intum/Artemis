package de.tum.in.www1.artemis.domain;

public interface ScoringStrategy {
    /**
     * Calculate the score for the given answer to the given question
     * @param question the question to score
     * @param submittedAnswer the answer to score
     * @return the resulting score (usually between 0.0 and question.getScore())
     */
    double calculateScore(Question question, SubmittedAnswer submittedAnswer);
}
