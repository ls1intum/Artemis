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

    @Modifying
    @Transactional
    @Query("""

            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (SELECT f FROM Feedback f JOIN f.result r WHERE r.submission IS NULL AND r.participation IS NULL)
                """)
    void deleteTextBlockForOrphanResults();

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM TextBlock tb
            WHERE tb.feedback IN (SELECT f FROM Feedback f JOIN f.result WHERE f.result IS NULL)
                """)
    void deleteTextBlockForEmptyFeedback();

    @Modifying
    @Transactional
    // old text block that is not part of latest rated results
    @Query("""
            DELETE FROM TextBlock tb
                WHERE tb.feedback IN (
                    SELECT f
                                    FROM Feedback f
                                        JOIN f.result r
                                        JOIN r.participation p
                                        LEFT JOIN p.exercise e
                                        LEFT JOIN e.course c
                                        WHERE f.result.id NOT IN (
                                        SELECT MAX(r2.id)
                FROM Result r2
                WHERE r2.participation.id = p.id AND r2.rated=true
                    )
                AND c.endDate < :deleteTo
                AND c.startDate > :deleteFrom
                                )
                """)
    void deleteTextBlockForRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM TextBlock tb
                WHERE tb.feedback IN (
                    SELECT f
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
