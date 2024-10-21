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

/**
 * Spring Data JPA repository for cleaning up old and orphaned feedback entries.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Repository
public interface FeedbackCleanupRepository extends ArtemisJpaRepository<Feedback, Long> {

    /**
     * Deletes {@link Feedback} entries where the associated {@link Result} has no submission and no participation.
     */
    @Modifying
    @Transactional // ok because of delete
    // Subquery ok
    @Query("""
            DELETE FROM Feedback f
            WHERE f.result IN (
                SELECT r
                FROM Result r
                WHERE r.submission IS NULL
                    AND r.participation IS NULL
                )
            """)
    void deleteFeedbackForOrphanResults();

    /**
     * Deletes {@link Feedback} entries with a {@code null} result.
     * Returns {@code void}
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Feedback f
            WHERE f.result IS NULL
            """)
    void deleteOrphanFeedback();

    /**
     * Deletes {@link Feedback} entries associated with rated {@link Result} that are not the latest rated result
     * for a {@link Participation}, within courses conducted between the specified date range.
     * This query removes old feedback entries that are not part of the latest rated results, for courses whose
     * end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Feedback f
            WHERE f.result IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                    WHERE r2.participation.id = p.id
                        AND r2.rated = true
                    )
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    void deleteOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes non-rated {@link Feedback} entries that are not the latest non-rated result, where the associated course's start and end dates
     * are between the specified date range.
     * This query removes old feedback entries that are not part of the latest non-rated result within courses whose end date is before
     * {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Feedback f
            WHERE f.result IN (
            SELECT r
                FROM Result r
                    LEFT JOIN r.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                    WHERE r2.participation.id = p.id)
                    AND r.rated=false
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    void deleteOldNonRatedFeedbackWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
