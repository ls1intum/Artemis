package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.LongFeedbackText;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface LongFeedbackRepository extends JpaRepository<LongFeedbackText, Long> {

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = { "feedback", "result", "participation" })
    Optional<LongFeedbackText> findWithEagerFeedbackAndResultAntParticipationById(final Long id);

    default LongFeedbackText findByIdWithFeedbackAndResultAndParticipationElseThrow(final Long id) {
        return findWithEagerFeedbackAndResultAntParticipationById(id).orElseThrow(() -> new EntityNotFoundException("long feedback text", id));
    }
}
