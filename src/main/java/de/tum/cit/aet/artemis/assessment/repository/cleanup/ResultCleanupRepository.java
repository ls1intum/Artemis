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
 * Spring Data JPA repository for the Result entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ResultCleanupRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * Deletes {@link Result} entries that have no participation and no submission.
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE
            FROM Result r
            WHERE r.participation IS NULL
                AND r.submission IS NULL
            """)
    void deleteResultWithoutParticipationAndSubmission();

    /**
     * Deletes non-rated {@link Result} entries that are not the latest result where the associated {@link Participation} and {@link Exercise} are not null,
     * and the course's start and end dates fall between the specified date range.
     * This query deletes non-rated results associated with exercises within courses whose end date is before
     * {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Result r
            WHERE r.rated = FALSE
                AND r.participation IS NOT NULL
                AND r.participation.exercise IS NOT NULL
                AND EXISTS (
                    SELECT 1
                    FROM Course c
                        LEFT JOIN c.exercises e
                    WHERE e = r.participation.exercise
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                )
                AND r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                    WHERE r2.participation = r.participation
                        AND r2.rated = FALSE
                )
            """)
    void deleteNonLatestNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes rated {@link Result} entries that are not the latest rated result for a {@link Participation}, within courses
     * conducted between the specified date range.
     * This query removes rated results that are not the most recent for a participation, for courses whose end date is
     * before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Result r
            WHERE r.rated = TRUE
                AND r.participation IS NOT NULL
                AND r.participation.exercise IS NOT NULL
                AND EXISTS (
                    SELECT 1
                    FROM Course c
                    LEFT JOIN c.exercises e
                    WHERE e = r.participation.exercise
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                )
                AND r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                    WHERE r2.participation = r.participation
                        AND r2.rated = TRUE
                )
            """)
    void deleteNonLatestRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
