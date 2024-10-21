package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.text.domain.TextBlock;

/**
 * Spring Data repository for the TextBlock entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TextBlockRepository extends ArtemisJpaRepository<TextBlock, String> {

    Set<TextBlock> findAllBySubmissionId(Long id);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllBySubmission_Id(Long submissionId);

    /**
     * Deletes {@link TextBlock} entries linked to {@link Feedback} where the associated {@link Result}
     * has no submission and no participation.
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (SELECT f FROM Feedback f JOIN f.result r WHERE r.submission IS NULL AND r.participation IS NULL)
            """)
    void deleteTextBlockForOrphanResults();

    /**
     * Deletes {@link TextBlock} entries linked to {@link Feedback} with a {@code null} result.
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (SELECT f FROM Feedback f WHERE f.result IS NULL)
            """)
    void deleteTextBlockForEmptyFeedback();

    /**
     * Deletes {@link TextBlock} entries associated with rated {@link Result} that are not the latest rated result
     * for a {@link Participation}, within courses conducted between the specified date range.
     * This query removes old text blocks that are not part of the latest rated results, for courses whose
     * end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (SELECT f
                                  FROM Feedback f
                                  JOIN f.result r
                                  JOIN r.participation p
                                  LEFT JOIN p.exercise e
                                  LEFT JOIN e.course c
                                  WHERE f.result.id NOT IN (SELECT MAX(r2.id)
                                                            FROM Result r2
                                                            WHERE r2.participation.id = p.id
                                                                AND r2.rated=true
                                                            )
                                      AND c.endDate < :deleteTo
                                      AND c.startDate > :deleteFrom
                                  )
            """)
    void deleteTextBlockForRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes {@link TextBlock} entries linked to non-rated {@link Result} where the associated course's start and end dates
     * are between the specified date range.
     * This query deletes text blocks for feedback associated with results that are not rated, within the courses
     * whose end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (SELECT f
                                  FROM Feedback f
                                  JOIN f.result r
                                  JOIN r.participation p
                                  LEFT JOIN p.exercise e
                                  LEFT JOIN e.course c
                                  WHERE r.rated=false
                                      AND c.endDate < :deleteTo
                                      AND c.startDate > :deleteFrom
                                  )
            """)
    void deleteTextBlockForNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
