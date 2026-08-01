package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
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
                LEFT JOIN FETCH result.submission
            WHERE longFeedback.feedback.id = :feedbackId
            """)
    Optional<LongFeedbackText> findWithFeedbackAndResultAndParticipationByFeedbackId(@Param("feedbackId") final Long feedbackId);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LongFeedbackText longFeedback
            WHERE longFeedback.feedback.id IN :feedbackIds
            """)
    void deleteByFeedbackIds(@Param("feedbackIds") List<Long> feedbackIds);

    @Modifying
    @Transactional // ok because of delete
    void deleteByFeedbackId(final Long feedbackId);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LongFeedbackText lft
            WHERE lft.feedback.result.id = :resultId
            """)
    void deleteByFeedbackResultId(@Param("resultId") long resultId);

    /**
     * Deletes long feedback texts associated with the supplied results.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null}, non-empty, and contains persisted result ids.
     * <p>
     * <b>Postcondition:</b> no long feedback text belongs to feedback for one of the supplied results.
     *
     * @param resultIds ids of the results whose long feedback texts are deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM LongFeedbackText longFeedback WHERE longFeedback.feedback.result.id IN :resultIds")
    void deleteByFeedbackResultIds(@Param("resultIds") final Collection<Long> resultIds);

    default LongFeedbackText findByFeedbackIdWithFeedbackAndResultAndParticipationElseThrow(final Long feedbackId) {
        return getValueElseThrow(findWithFeedbackAndResultAndParticipationByFeedbackId(feedbackId), feedbackId);
    }
}
