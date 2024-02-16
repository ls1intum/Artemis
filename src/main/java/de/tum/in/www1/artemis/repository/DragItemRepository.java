package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragItem;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the DragItem entity.
 */
@Repository
public interface DragItemRepository extends JpaRepository<DragItem, Long> {

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = "question")
    Optional<DragItem> findWithEagerQuestionById(Long id);

    default DragItem findByIdElseThrow(Long dragItemId) {
        return findWithEagerQuestionById(dragItemId).orElseThrow(() -> new EntityNotFoundException("DragItem", dragItemId));
    }
}
