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
                LEFT JOIN FETCH longFeedback.feedback feedback
                LEFT JOIN FETCH feedback.result result
                LEFT JOIN FETCH result.participation
            WHERE
                longFeedback.id = :id
            """)
    Optional<LongFeedbackText> findWithFeedbackAndResultAndParticipationById(final Long id);

    default LongFeedbackText findByIdWithFeedbackAndResultAndParticipationElseThrow(final Long id) {
        return findWithFeedbackAndResultAndParticipationById(id).orElseThrow(() -> new EntityNotFoundException("long feedback text", id));
    }
}
