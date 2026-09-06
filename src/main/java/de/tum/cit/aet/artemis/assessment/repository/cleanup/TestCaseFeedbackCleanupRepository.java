package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for cleaning up old and orphaned test-case feedback entries.
 * Mirrors {@link FeedbackCleanupRepository} for the typed automatic feedback of programming exercises.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface TestCaseFeedbackCleanupRepository extends ArtemisJpaRepository<TestCaseFeedback, Long> {

    /**
     * Deletes {@link TestCaseFeedback} entries where the associated result has no submission or its submission has no participation.
     *
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TestCaseFeedback f
            WHERE f.result IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                WHERE s IS NULL
                   OR p IS NULL
            )
            """)
    int deleteTestCaseFeedbackForOrphanResults();

    /**
     * Counts {@link TestCaseFeedback} entries where the associated result has no submission or its submission has no participation.
     *
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(f)
            FROM TestCaseFeedback f
                LEFT JOIN f.result r
                LEFT JOIN r.submission s
                LEFT JOIN s.participation p
            WHERE s IS NULL
                OR p IS NULL
            """)
    int countTestCaseFeedbackForOrphanResults();

    /**
     * Deletes {@link TestCaseFeedback} entries of rated results that are not the latest rated result of their
     * participation, within courses conducted between the specified date range. See
     * {@link FeedbackCleanupRepository#deleteOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TestCaseFeedback f
            WHERE f.result IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                    JOIN Exercise e ON r.exerciseId = e.id
                    JOIN e.course c
                WHERE r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                        LEFT JOIN r2.submission s2
                        LEFT JOIN s2.participation p2
                    WHERE p2.id = p.id
                        AND r2.rated = TRUE
                    )
                    AND r.rated = TRUE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    int deleteOldTestCaseFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Counts the entries {@link #deleteOldTestCaseFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween} would delete.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(f)
            FROM TestCaseFeedback f
            WHERE f.result IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                    JOIN Exercise e ON r.exerciseId = e.id
                    JOIN e.course c
                WHERE r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                        LEFT JOIN r2.submission s2
                        LEFT JOIN s2.participation p2
                    WHERE p2.id = p.id
                        AND r2.rated = TRUE
                    )
                    AND r.rated = TRUE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    int countOldTestCaseFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes {@link TestCaseFeedback} entries of non-rated results that are not the latest non-rated result of
     * their participation, within courses conducted between the specified date range. See
     * {@link FeedbackCleanupRepository#deleteOldNonRatedFeedbackWhereCourseDateBetween}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TestCaseFeedback f
            WHERE f.result IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                    JOIN Exercise e ON r.exerciseId = e.id
                    JOIN e.course c
                WHERE r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                        LEFT JOIN r2.submission s2
                        LEFT JOIN s2.participation p2
                    WHERE p2.id = p.id
                        AND r2.rated = FALSE
                    )
                    AND r.rated = FALSE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    int deleteOldNonRatedTestCaseFeedbackWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Counts the entries {@link #deleteOldNonRatedTestCaseFeedbackWhereCourseDateBetween} would delete.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(f)
            FROM TestCaseFeedback f
            WHERE f.result IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                    JOIN Exercise e ON r.exerciseId = e.id
                    JOIN e.course c
                WHERE r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                        LEFT JOIN r2.submission s2
                        LEFT JOIN s2.participation p2
                    WHERE p2.id = p.id
                        AND r2.rated = FALSE
                    )
                    AND r.rated = FALSE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    int countOldNonRatedTestCaseFeedbackWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
