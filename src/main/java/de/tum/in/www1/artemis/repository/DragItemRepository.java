package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragItem;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the DragItem entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface DragItemRepository extends ArtemisJpaRepository<DragItem, Long> {

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = "question")
    Optional<DragItem> findWithEagerQuestionById(Long id);

    default DragItem findWithEagerQuestionByIdElseThrow(Long dragItemId) {
        return findWithEagerQuestionById(dragItemId).orElseThrow(() -> new EntityNotFoundException("DragItem", dragItemId));
    }
}
