package de.tum.cit.aet.artemis.quiz.repository;

import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswerSelection;

/**
 * Narrow projections used to calculate quiz statistics directly from results and submitted-answer selections.
 */
public final class QuizStatisticProjections {

    private QuizStatisticProjections() {
    }

    /**
     * A score bucket and its participant count for one normalized rating bucket.
     */
    public interface PointBucket {

        boolean getRated();

        double getScore();

        long getParticipantCount();
    }

    /**
     * Participant and correctness counts for one quiz question and normalized rating bucket.
     */
    public interface QuizOverviewAggregate {

        long getQuestionId();

        boolean getRated();

        long getParticipantCount();

        long getCorrectCount();
    }

    /**
     * A submitted-answer selection, its score, and the normalized rating bucket of its result.
     */
    public interface RatedSelection {

        SubmittedAnswerSelection getSelection();

        Double getScoreInPoints();

        boolean getRated();
    }
}
