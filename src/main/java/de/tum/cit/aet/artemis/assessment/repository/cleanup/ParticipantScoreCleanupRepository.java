package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * Spring Data JPA repository for cleaning up old and orphaned participant scores.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Repository
public interface ParticipantScoreCleanupRepository extends ArtemisJpaRepository<ParticipantScore, Long> {

    /**
     * Deletes {@link ParticipantScore} entries where the associated {@link Result} is not the latest rated result
     * for a {@link Participation}, within courses conducted between the specified date range.
     * This query removes participant scores linked to results that are not the most recent rated results, for courses
     * whose end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ParticipantScore ps
            WHERE ps.lastResult IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                    WHERE r2.participation.id = p.id
                        AND r2.rated=true
                    )
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    void deleteParticipantScoresForNonLatestLastResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes {@link ParticipantScore} entries where the associated last rated {@link Result} is not the latest rated result
     * for a {@link Participation}, within courses conducted between the specified date range.
     * This query removes participant scores linked to rated results that are not the most recent rated results, for courses
     * whose end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ParticipantScore ps
            WHERE ps.lastRatedResult IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE r.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                    WHERE r2.participation.id = p.id
                        AND r2.rated = TRUE
                    )
                AND c.endDate < :deleteTo
                AND c.startDate > :deleteFrom
                )
            """)
    void deleteParticipantScoresForNonLatestLastRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes {@link ParticipantScore} entries where the associated {@link Result} is the latest result and is non-rated,
     * and the course's start and end dates are between the specified date range.
     * This query deletes participant scores for non-rated results within courses whose end date is before
     * {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ParticipantScore ps
            WHERE ps.lastResult IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE r.rated = FALSE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom                                                                       )
            """)
    void deleteParticipantScoresForLatestNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes {@link ParticipantScore} entries where the associated {@link Result} is non-rated, even though
     * it is marked as the last rated result, to prevent potential integrity violations.
     * The deletion is based on courses whose start and end dates fall within the specified range.
     * This scenario should not normally occur, as non-rated results cannot be marked as rated, but the
     * method ensures cleanup in case of any potential integrity issues.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ParticipantScore ps
            WHERE ps.lastRatedResult IN (
                SELECT r
                FROM Result r
                    LEFT JOIN r.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE r.rated = FALSE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    void deleteParticipantScoresForNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
