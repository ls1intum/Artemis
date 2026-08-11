package de.tum.cit.aet.artemis.quiz.test_repository;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.FlatResult;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.PointBucket;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.QuestionAggregate;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.QuizOverviewAggregate;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.RatedSelection;

/**
 * Candidate queries for computing quiz statistics on demand instead of maintaining the {@code quiz_statistic} / {@code quiz_statistic_counter} tables.
 * <p>
 * Every query here is plain JPQL — no native SQL, no database views, no stored procedures and no vendor-specific function — so the same statement runs unchanged on MySQL and
 * PostgreSQL. Each one covers exactly one statistics page, so opening the multiple-choice page never pays for the drag-and-drop or short-answer page.
 * <p>
 * All of them share the same notion of "the results that count", which mirrors {@code QuizStatisticService#recalculateStatistics}: per participation, the latest rated result and
 * the latest unrated result. That reduction is expressed as a correlated {@code MAX(completionDate)} subquery, which is the only formulation of "top-1 per group" that is portable
 * across both databases without window functions or derived tables.
 */
@Lazy
@Repository
@Primary
public interface SubmittedAnswerTestRepository extends SubmittedAnswerRepository {

    /**
     * The complete quiz point statistic in one round trip: for every reached score, how many participants reached it, split by rated / unrated. The caller only has to fold the
     * percentage scores into the integer point buckets the chart draws, which is at most a few dozen rows.
     *
     * @param exerciseId the quiz exercise
     * @return one row per (rated, score) combination that at least one participant reached
     */
    @Query("""
            SELECT result.rated AS rated,
                result.score AS score,
                COUNT(result.id) AS participantCount
            FROM Result result
                JOIN result.submission submission
            WHERE result.exerciseId = :exerciseId
                AND result.rated IS NOT NULL
                AND result.score IS NOT NULL
                AND result.completionDate = (
                    SELECT MAX(latest.completionDate)
                    FROM Result latest
                        JOIN latest.submission latestSubmission
                    WHERE latestSubmission.participation.id = submission.participation.id
                        AND latest.rated = result.rated
                )
            GROUP BY result.rated, result.score
            """)
    List<PointBucket> findPointStatistic(@Param("exerciseId") long exerciseId);

    /**
     * The same data as {@link #findPointStatistic}, but without the correlated subquery: every result of the quiz is streamed out as four scalars and the
     * latest-per-participation reduction happens in Java. Kept as a comparison baseline — it trades one row per result for a much simpler plan.
     *
     * @param exerciseId the quiz exercise
     * @return one row per result of the quiz
     */
    @Query("""
            SELECT submission.participation.id AS participationId,
                result.rated AS rated,
                result.score AS score,
                result.completionDate AS completionDate
            FROM Result result
                JOIN result.submission submission
            WHERE result.exerciseId = :exerciseId
                AND result.rated IS NOT NULL
                AND result.score IS NOT NULL
            """)
    List<FlatResult> findResultsForPointStatistic(@Param("exerciseId") long exerciseId);

    /**
     * Participant and full-credit counts for a single question, split by rated / unrated. The full-credit count reads the persisted {@code submitted_answer.score_in_points}, so
     * the database answers "how many students got this question completely right" without the server re-running any scoring strategy.
     *
     * @param questionId     the quiz question the statistics page shows
     * @param questionPoints the question's maximum points; a submitted answer that reached them is a fully correct answer
     * @return one row for the rated and one for the unrated participants
     */
    @Query("""
            SELECT result.rated AS rated,
                COUNT(answer.id) AS participantCount,
                SUM(CASE WHEN answer.scoreInPoints >= :questionPoints THEN 1 ELSE 0 END) AS correctCount
            FROM SubmittedAnswer answer
                JOIN answer.submission submission
                JOIN Result result ON result.submission = submission
            WHERE answer.quizQuestion.id = :questionId
                AND result.rated IS NOT NULL
                AND result.completionDate = (
                    SELECT MAX(latest.completionDate)
                    FROM Result latest
                        JOIN latest.submission latestSubmission
                    WHERE latestSubmission.participation.id = submission.participation.id
                        AND latest.rated = result.rated
                )
            GROUP BY result.rated
            """)
    List<QuestionAggregate> findQuestionAggregate(@Param("questionId") long questionId, @Param("questionPoints") double questionPoints);

    /**
     * Participant and full-credit counts for every question of a quiz at once, split by rated / unrated. This is the whole data set of the quiz statistics overview page: one bar
     * per question, no per-element counters.
     *
     * @param exerciseId the quiz exercise
     * @return one row per (question, rated) combination
     */
    @Query("""
            SELECT question.id AS questionId,
                result.rated AS rated,
                COUNT(answer.id) AS participantCount,
                SUM(CASE WHEN answer.scoreInPoints >= question.points THEN 1 ELSE 0 END) AS correctCount
            FROM SubmittedAnswer answer
                JOIN answer.quizQuestion question
                JOIN answer.submission submission
                JOIN Result result ON result.submission = submission
            WHERE question.exercise.id = :exerciseId
                AND result.rated IS NOT NULL
                AND result.completionDate = (
                    SELECT MAX(latest.completionDate)
                    FROM Result latest
                        JOIN latest.submission latestSubmission
                    WHERE latestSubmission.participation.id = submission.participation.id
                        AND latest.rated = result.rated
                )
            GROUP BY question.id, result.rated
            """)
    List<QuizOverviewAggregate> findQuestionAggregatesForQuiz(@Param("exerciseId") long exerciseId);

    /**
     * The raw submitted selections for a single question. This is the one part of a question statistic that cannot be aggregated in SQL portably: the per-answer-option,
     * per-drop-location and per-spot counters all live inside the {@code submitted_answer.selection} JSON document, and the short-answer counters additionally need the fuzzy
     * string comparison that decides whether a typed text matches a solution.
     * <p>
     * The projection deliberately selects only the JSON column and the rated flag, so no submitted answer, submission, participation or result entity is materialized.
     *
     * @param questionId the quiz question the statistics page shows
     * @return one row per counted submitted answer
     */
    @Query("""
            SELECT answer.selection AS selection,
                result.rated AS rated
            FROM SubmittedAnswer answer
                JOIN answer.submission submission
                JOIN Result result ON result.submission = submission
            WHERE answer.quizQuestion.id = :questionId
                AND result.rated IS NOT NULL
                AND result.completionDate = (
                    SELECT MAX(latest.completionDate)
                    FROM Result latest
                        JOIN latest.submission latestSubmission
                    WHERE latestSubmission.participation.id = submission.participation.id
                        AND latest.rated = result.rated
                )
            """)
    List<RatedSelection> findSelectionsForQuestion(@Param("questionId") long questionId);
}
