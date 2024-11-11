package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.text.domain.TextBlock;

/**
 * Spring Data JPA repository for cleaning up old and orphaned text block entries.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Repository
public interface TextBlockCleanupRepository extends ArtemisJpaRepository<TextBlock, Long> {

    /**
     * Deletes {@link TextBlock} entries linked to {@link Feedback} where the associated {@link Result}
     * has no submission and no participation.
     *
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (
                SELECT f
                FROM Feedback f
                    LEFT JOIN f.result r
                WHERE r.submission IS NULL
                    AND r.participation IS NULL
                )
            """)
    int deleteTextBlockForOrphanResults();

    /**
     * Deletes {@link TextBlock} entries linked to {@link Feedback} with a {@code null} result.
     *
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (
                SELECT f
                FROM Feedback f
                WHERE f.result IS NULL
                )
            """)
    int deleteTextBlockForEmptyFeedback();

    /**
     * Deletes {@link TextBlock} entries associated with rated {@link Result} that are not the latest rated result
     * for a {@link Participation}, within courses conducted between the specified date range.
     * This query removes old text blocks that are not part of the latest rated results, for courses whose
     * end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (
                SELECT f
                FROM Feedback f
                    LEFT JOIN f.result r
                    LEFT JOIN r.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE f.result.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                    WHERE r2.participation.id = p.id
                        AND r2.rated = TRUE
                )
                    AND r.rated = TRUE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
            )
            """)
    int deleteTextBlockForRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes {@link TextBlock} entries linked to non-rated {@link Result} that are not the latest non-rated result
     * for a {@link Participation}, where the associated course's start and end dates
     * are between the specified date range.
     * This query deletes text blocks for feedback associated with results that are not rated, within the courses
     * whose end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (
                SELECT f
                FROM Feedback f
                    LEFT JOIN f.result r
                    LEFT JOIN r.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE f.result.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                    WHERE r2.participation.id = p.id
                        AND r2.rated = FALSE
                    )
                    AND r.rated = FALSE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    int deleteTextBlockForNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
