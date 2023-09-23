package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.LongFeedbackText;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface LongFeedbackTextRepository extends JpaRepository<LongFeedbackText, Long> {

    @Query("""
            SELECT longFeedback
            FROM LongFeedbackText longFeedback
            WHERE longFeedback.feedback.id = :feedbackId
            """)
    Optional<LongFeedbackText> findByFeedbackId(long feedbackId);

    @Query("""
            SELECT longFeedback
            FROM LongFeedbackText longFeedback
                LEFT JOIN FETCH longFeedback.feedback feedback
                LEFT JOIN FETCH feedback.result result
                LEFT JOIN FETCH result.participation
            WHERE
                longFeedback.feedback.id = :feedbackId
            """)
    Optional<LongFeedbackText> findWithFeedbackAndResultAndParticipationByFeedbackId(final Long feedbackId);

    default LongFeedbackText findByFeedbackIdWithFeedbackAndResultAndParticipationElseThrow(final Long feedbackId) {
        return findWithFeedbackAndResultAndParticipationByFeedbackId(feedbackId).orElseThrow(() -> new EntityNotFoundException("long feedback text", feedbackId));
    }
}
