package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.DragItem;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the DragItem entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragItemRepository extends JpaRepository<DragItem, Long> {

}
