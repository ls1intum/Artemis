package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface LongFeedbackTextRepository extends ArtemisJpaRepository<LongFeedbackText, Long> {

    @Query("""
            SELECT longFeedback
            FROM LongFeedbackText longFeedback
            WHERE longFeedback.feedback.id = :feedbackId
            """)
    Optional<LongFeedbackText> findByFeedbackId(@Param("feedbackId") long feedbackId);

    @Query("""
            SELECT longFeedback
            FROM LongFeedbackText longFeedback
                JOIN longFeedback.feedback feedback
            WHERE feedback.id IN :feedbackIds
            """)
    List<LongFeedbackText> findByFeedbackIds(@Param("feedbackIds") List<Long> feedbackIds);

    @Query("""
            SELECT longFeedback
            FROM LongFeedbackText longFeedback
                LEFT JOIN FETCH longFeedback.feedback feedback
                LEFT JOIN FETCH feedback.result result
                LEFT JOIN FETCH result.participation
            WHERE longFeedback.feedback.id = :feedbackId
            """)
    Optional<LongFeedbackText> findWithFeedbackAndResultAndParticipationByFeedbackId(@Param("feedbackId") final Long feedbackId);

    default LongFeedbackText findByFeedbackIdWithFeedbackAndResultAndParticipationElseThrow(final Long feedbackId) {
        return getValueElseThrow(findWithFeedbackAndResultAndParticipationByFeedbackId(feedbackId), feedbackId);
    }

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM LongFeedbackText lft
                                                                                       WHERE lft.feedback.id IN (
                                                                                           SELECT f.id
                                                                                           FROM Feedback f
                                                                                           WHERE f.result.participation IS NULL AND f.result.submission IS NULL
                                                                                       )
                """)
    void deleteLongFeedbackTextForOrphanRepository();

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM LongFeedbackText lft
                                WHERE lft.feedback IN (
                                    SELECT f
                                    FROM Feedback f
                                    WHERE f.result IS NULL
                                )
                """)
    void deleteLongFeedbackTextForEmptyFeedback();

    // delete all rated results that are not latest rated for a participation for courses conducted within a specific date range, also delete all related feedback entities
    // beforehand
    // old long feedback text that is not part of latest rated results
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM LongFeedbackText lft
                WHERE lft.feedback IN (
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
    void deleteLongFeedbackTextForRatedResultsBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM LongFeedbackText lft
                WHERE lft.feedback IN (
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
    void deleteLongFeedbackTextForNonRatedResultsWhereCourseBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
