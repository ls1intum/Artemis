package de.tum.cit.aet.artemis.quiz.util;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswerSelection;

/**
 * Spring Data interface projections for the "compute quiz statistics on the fly" experiment.
 * <p>
 * These are deliberately narrow: every projection carries only the columns one statistics page needs, so the database never has to materialize a submitted answer, a submission or
 * a
 * result entity to render a chart. They live in the test sources because the experiment only evaluates whether the {@code quiz_statistic} / {@code quiz_statistic_counter} tables
 * can be dropped; if that decision is taken, the same shapes move to {@code de.tum.cit.aet.artemis.quiz.dto} as records.
 */
public final class QuizStatisticProjections {

    private QuizStatisticProjections() {
    }

    /**
     * One bucket of the quiz point statistic: how many participants reached exactly this score, split by rated / unrated.
     */
    public interface PointBucket {

        Boolean getRated();

        Double getScore();

        long getParticipantCount();
    }

    /**
     * A single result of the quiz, flattened to the four scalars the point statistic needs. Used by the "project and fold in Java" variant, which does the
     * latest-result-per-participation reduction on the server instead of in a correlated subquery.
     */
    public interface FlatResult {

        long getParticipationId();

        Boolean getRated();

        Double getScore();

        ZonedDateTime getCompletionDate();
    }

    /**
     * The rated / unrated participant and full-credit counts of a single question. {@code correctCount} counts the submitted answers that scored the question's full points, which
     * is exactly the definition of {@code QuizQuestion#isAnswerCorrect} — but read from the persisted {@code submitted_answer.score_in_points} instead of re-running the scoring
     * strategy.
     */
    public interface QuestionAggregate {

        Boolean getRated();

        long getParticipantCount();

        long getCorrectCount();
    }

    /**
     * The same counts as {@link QuestionAggregate}, but for every question of the quiz at once. This is what the quiz statistics overview page draws: one bar per question showing
     * how many participants answered it completely correctly. It needs no per-element counters.
     */
    public interface QuizOverviewAggregate {

        long getQuestionId();

        Boolean getRated();

        long getParticipantCount();

        long getCorrectCount();
    }

    /**
     * A single student's submitted selection for one question, plus whether the owning result was rated. This is the only projection that has to leave the database as JSON: the
     * per-answer-option / per-drop-location / per-spot counters cannot be derived from scalar columns.
     */
    public interface RatedSelection {

        SubmittedAnswerSelection getSelection();

        Boolean getRated();
    }
}
