package de.tum.cit.aet.artemis.quiz.repository;

import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswerSelection;

/**
 * Narrow projections used to calculate quiz statistics directly from results and submitted-answer selections.
 */
public final class QuizStatisticProjections {

    private QuizStatisticProjections() {
    }

    public interface PointBucket {

        Boolean getRated();

        Double getScore();

        long getParticipantCount();
    }

    public interface ParticipantCount {

        Boolean getRated();

        long getParticipantCount();
    }

    public interface QuestionAggregate {

        Boolean getRated();

        long getParticipantCount();

        long getCorrectCount();
    }

    public interface QuizOverviewAggregate {

        long getQuestionId();

        Boolean getRated();

        long getParticipantCount();

        long getCorrectCount();
    }

    public interface RatedSelection {

        SubmittedAnswerSelection getSelection();

        Boolean getRated();
    }
}
