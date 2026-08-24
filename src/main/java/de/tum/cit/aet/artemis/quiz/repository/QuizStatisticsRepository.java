package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticProjections.PointBucket;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticProjections.QuestionAggregate;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticProjections.QuizOverviewAggregate;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticProjections.RatedSelection;

/**
 * Read-only queries for calculating quiz statistics from the latest rated and unrated result of every participation.
 * <p>
 * A {@code null} rated flag has the same meaning as {@code false}, matching {@code Result#isRated()}. The result id is
 * the deterministic tie-breaker when two results have the same completion date.
 * <p>
 * The correlated anti-joins are required because results reference submissions while the latest-result rule is scoped to a participation and rating bucket. The normalized lookup
 * is
 * supported by {@code idx_submission_participation_submission_date} and {@code idx_result_submission_completion_date}; duplicate-answer anti-joins remain separate because they
 * select the greatest answer id within one submission and question.
 */
@Profile(PROFILE_CORE)
@Lazy
@org.springframework.stereotype.Repository
public interface QuizStatisticsRepository extends Repository<SubmittedAnswer, Long> {

    /**
     * Groups the latest completed result of every participation and rated/unrated bucket by score.
     * <p>
     * {@code null} rated flags belong to the unrated bucket. Completion date defines recency and result id breaks ties.
     *
     * @param exerciseId the quiz exercise id
     * @return score buckets with their participant counts
     */
    @Query("""
            SELECT COALESCE(result.rated, false) AS rated,
                result.score AS score,
                COUNT(result.id) AS participantCount
            FROM Result result
                JOIN result.submission submission
            WHERE result.exerciseId = :exerciseId
                AND result.score IS NOT NULL
                AND result.completionDate IS NOT NULL
                AND NOT EXISTS (
                    SELECT newer.id
                    FROM Result newer
                        JOIN newer.submission newerSubmission
                    WHERE newerSubmission.participation.id = submission.participation.id
                        AND COALESCE(newer.rated, false) = COALESCE(result.rated, false)
                        AND newer.score IS NOT NULL
                        AND newer.completionDate IS NOT NULL
                        AND (newer.completionDate > result.completionDate
                            OR (newer.completionDate = result.completionDate AND newer.id > result.id))
                )
            GROUP BY COALESCE(result.rated, false), result.score
            """)
    List<PointBucket> findPointStatistic(@Param("exerciseId") long exerciseId);

    /**
     * Counts participants and fully correct answers for one question.
     * <p>
     * If a submission contains duplicate answers for the question, only the answer with the greatest id is counted. Results use the same latest-per-participation and rated-bucket
     * rule as {@link #findPointStatistic(long)}.
     *
     * @param questionId     the quiz question id
     * @param questionPoints the points required for a fully correct answer
     * @return one aggregate for each populated rated/unrated bucket
     */
    @Query("""
            SELECT COALESCE(result.rated, false) AS rated,
                COUNT(answer.id) AS participantCount,
                SUM(CASE WHEN answer.scoreInPoints >= :questionPoints THEN 1 ELSE 0 END) AS correctCount
            FROM SubmittedAnswer answer
                JOIN answer.submission submission
                JOIN Result result ON result.submission = submission
            WHERE answer.quizQuestion.id = :questionId
                AND NOT EXISTS (
                    SELECT duplicateAnswer.id
                    FROM SubmittedAnswer duplicateAnswer
                    WHERE duplicateAnswer.submission = submission
                        AND duplicateAnswer.quizQuestion = answer.quizQuestion
                        AND duplicateAnswer.id > answer.id
                )
                AND result.score IS NOT NULL
                AND result.completionDate IS NOT NULL
                AND NOT EXISTS (
                    SELECT newer.id
                    FROM Result newer
                        JOIN newer.submission newerSubmission
                    WHERE newerSubmission.participation.id = submission.participation.id
                        AND COALESCE(newer.rated, false) = COALESCE(result.rated, false)
                        AND newer.score IS NOT NULL
                        AND newer.completionDate IS NOT NULL
                        AND (newer.completionDate > result.completionDate
                            OR (newer.completionDate = result.completionDate AND newer.id > result.id))
                )
            GROUP BY COALESCE(result.rated, false)
            """)
    List<QuestionAggregate> findQuestionAggregate(@Param("questionId") long questionId, @Param("questionPoints") double questionPoints);

    /**
     * Counts participants and fully correct answers for every question in a quiz.
     * <p>
     * Duplicate answers and superseded results are resolved by the same rules as {@link #findQuestionAggregate(long, double)}.
     *
     * @param exerciseId the quiz exercise id
     * @return aggregates grouped by question and rated/unrated bucket
     */
    @Query("""
            SELECT question.id AS questionId,
                COALESCE(result.rated, false) AS rated,
                COUNT(answer.id) AS participantCount,
                SUM(CASE WHEN answer.scoreInPoints >= question.points THEN 1 ELSE 0 END) AS correctCount
            FROM SubmittedAnswer answer
                JOIN answer.quizQuestion question
                JOIN answer.submission submission
                JOIN Result result ON result.submission = submission
            WHERE question.exercise.id = :exerciseId
                AND result.score IS NOT NULL
                AND result.completionDate IS NOT NULL
                AND NOT EXISTS (
                    SELECT duplicateAnswer.id
                    FROM SubmittedAnswer duplicateAnswer
                    WHERE duplicateAnswer.submission = submission
                        AND duplicateAnswer.quizQuestion = question
                        AND duplicateAnswer.id > answer.id
                )
                AND NOT EXISTS (
                    SELECT newer.id
                    FROM Result newer
                        JOIN newer.submission newerSubmission
                    WHERE newerSubmission.participation.id = submission.participation.id
                        AND COALESCE(newer.rated, false) = COALESCE(result.rated, false)
                        AND newer.score IS NOT NULL
                        AND newer.completionDate IS NOT NULL
                        AND (newer.completionDate > result.completionDate
                            OR (newer.completionDate = result.completionDate AND newer.id > result.id))
                )
            GROUP BY question.id, COALESCE(result.rated, false)
            """)
    List<QuizOverviewAggregate> findQuestionAggregatesForQuiz(@Param("exerciseId") long exerciseId);

    /**
     * Loads the selections used to calculate component counters for one question.
     * <p>
     * Only the greatest-id answer per submission/question and the latest completed result per participation/rated bucket contribute.
     * At most one row per participation and rating bucket is returned, so materializing the result remains bounded by the participant population and avoids a transactional
     * streaming
     * boundary in the service.
     *
     * @param questionId the quiz question id
     * @return the selected answer values and their rated/unrated buckets
     */
    @Query("""
            SELECT answer.selection AS selection,
                COALESCE(result.rated, false) AS rated
            FROM SubmittedAnswer answer
                JOIN answer.submission submission
                JOIN Result result ON result.submission = submission
            WHERE answer.quizQuestion.id = :questionId
                AND result.score IS NOT NULL
                AND result.completionDate IS NOT NULL
                AND NOT EXISTS (
                    SELECT duplicateAnswer.id
                    FROM SubmittedAnswer duplicateAnswer
                    WHERE duplicateAnswer.submission = submission
                        AND duplicateAnswer.quizQuestion = answer.quizQuestion
                        AND duplicateAnswer.id > answer.id
                )
                AND NOT EXISTS (
                    SELECT newer.id
                    FROM Result newer
                        JOIN newer.submission newerSubmission
                    WHERE newerSubmission.participation.id = submission.participation.id
                        AND COALESCE(newer.rated, false) = COALESCE(result.rated, false)
                        AND newer.score IS NOT NULL
                        AND newer.completionDate IS NOT NULL
                        AND (newer.completionDate > result.completionDate
                            OR (newer.completionDate = result.completionDate AND newer.id > result.id))
                )
            """)
    List<RatedSelection> findSelectionsForQuestion(@Param("questionId") long questionId);
}
