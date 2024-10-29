package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

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
     * Retrieves the IDs of the latest results that are rated or unrated, grouped by participation.
     *
     * @param rated specifies whether to filter results based on their rated status.
     * @return a list of IDs representing the latest results for each participation that match the rated status.
     */
    @Query("""
            SELECT MAX(r.id)
            FROM Result r
            WHERE r.rated = :rated
            GROUP BY r.participation
            """)
    List<Long> getLatestResultsWhereRatedGroupedByParticipation(@Param("rated") boolean rated);

    /**
     * Deletes non-rated {@link Result} entries that are not the latest result where the associated {@link Participation} and {@link Exercise} are not null,
     * and the course's start and end dates fall between the specified date range.
     * This query deletes non-rated results associated with exercises within courses whose end date is before
     * {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom              the start date for selecting courses
     * @param deleteTo                the end date for selecting courses
     * @param latestNonRatedResultIds ids of latest non-rated results pro participation(participation not provided)
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
                AND r.id NOT IN :latestNonRatedResultIds
            """)
    void deleteNonLatestNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo,
            @Param("latestNonRatedResultIds") List<Long> latestNonRatedResultIds);

    /**
     * Deletes rated {@link Result} entries that are not the latest rated result for a {@link Participation}, within courses
     * conducted between the specified date range.
     * This query removes rated results that are not the most recent for a participation, for courses whose end date is
     * before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom           the start date for selecting courses
     * @param deleteTo             the end date for selecting courses
     * @param latestRatedResultIds ids of latest rated results pro participation(participation not provided)
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
                AND r.id NOT IN :latestRatedResultIds
            """)
    void deleteNonLatestRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo,
            @Param("latestRatedResultIds") List<Long> latestRatedResultIds);
}
