package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * Spring Data JPA repository for cleaning up old and orphaned results.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Repository
public interface ResultCleanupRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * Deletes {@link Result} entries that have no participation and no submission.
     * Now a result is considered orphaned if its submission is null
     * or if its submission exists but its participation is null.
     *
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE
            FROM Result r
            WHERE r.submission IS NULL OR r.submission.participation IS NULL
            """)
    int deleteResultWithoutParticipationAndSubmission();

    /**
     * Counts {@link Result} entries that have no participation and no submission.
     * Now a result is considered orphaned if its submission is null
     * or if its submission exists but its participation is null.
     *
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(r)
            FROM Result r
            WHERE r.submission IS NULL OR r.submission.participation IS NULL
            """)
    int countResultWithoutParticipationAndSubmission();

    /**
     * Deletes non-rated {@link Result} entries that are not the latest result where the associated
     * {@link Participation} and {@link Exercise} are not null, and the course's start and end dates fall
     * between the specified date range.
     * This query deletes non-rated results associated with exercises within courses whose end date is before
     * {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Result r
            WHERE r.rated = FALSE
                AND r.submission.participation IS NOT NULL
                AND r.submission.participation.exercise IS NOT NULL
                AND EXISTS (
                    SELECT 1
                    FROM Course c
                        LEFT JOIN c.exercises e
                    WHERE e = r.submission.participation.exercise
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                AND r.id NOT IN (
                    SELECT max_id
                    FROM (
                        SELECT MAX(r2.id) AS max_id
                        FROM Result r2
                        WHERE r2.rated = FALSE
                        GROUP BY r2.submission.participation.id
                        )
                    )
            """)
    int deleteNonLatestNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Counts non-rated {@link Result} entries that are not the latest result where the associated
     * {@link Participation} and {@link Exercise} are not null, and the course's start and end dates fall
     * between the specified date range.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(r)
            FROM Result r
            WHERE r.rated = FALSE
                AND r.submission.participation IS NOT NULL
                AND r.submission.participation.exercise IS NOT NULL
                AND EXISTS (
                    SELECT 1
                    FROM Course c
                        LEFT JOIN c.exercises e
                    WHERE e = r.submission.participation.exercise
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                AND r.id NOT IN (
                    SELECT max_id
                    FROM (
                        SELECT MAX(r2.id) AS max_id
                        FROM Result r2
                        WHERE r2.rated = FALSE
                        GROUP BY r2.submission.participation.id
                        )
                    )
            """)
    int countNonLatestNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes rated {@link Result} entries that are not the latest rated result for a {@link Participation},
     * within courses conducted between the specified date range.
     * This query removes rated results that are not the most recent for a participation, for courses whose
     * end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Result r
            WHERE r.rated = TRUE
                AND r.submission.participation IS NOT NULL
                AND r.submission.participation.exercise IS NOT NULL
                AND EXISTS (
                    SELECT 1
                    FROM Course c
                        LEFT JOIN c.exercises e
                    WHERE e = r.submission.participation.exercise
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                AND r.id NOT IN (
                    SELECT max_id
                    FROM (
                        SELECT MAX(r2.id) AS max_id
                        FROM Result r2
                        WHERE r2.rated = TRUE
                        GROUP BY r2.submission.participation.id
                        )
                    )
            """)
    int deleteNonLatestRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Counts rated {@link Result} entries that are not the latest rated result for a {@link Participation},
     * within courses conducted between the specified date range.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(r)
            FROM Result r
            WHERE r.rated = TRUE
                AND r.submission.participation IS NOT NULL
                AND r.submission.participation.exercise IS NOT NULL
                AND EXISTS (
                    SELECT 1
                    FROM Course c
                        LEFT JOIN c.exercises e
                    WHERE e = r.submission.participation.exercise
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                AND r.id NOT IN (
                    SELECT max_id
                    FROM (
                        SELECT MAX(r2.id) AS max_id
                        FROM Result r2
                        WHERE r2.rated = TRUE
                        GROUP BY r2.submission.participation.id
                        )
                    )
            """)
    int countNonLatestRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
