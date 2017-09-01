package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.DragItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the DragItem entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragItemRepository extends JpaRepository<DragItem, Long> {

}
