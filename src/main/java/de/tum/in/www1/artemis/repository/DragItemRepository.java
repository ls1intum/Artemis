package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragItem;

/**
 * Spring Data JPA repository for the DragItem entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragItemRepository extends JpaRepository<DragItem, Long> {

}
