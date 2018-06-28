package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.DragAndDropAssignment;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the DragAndDropAssignment entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropAssignmentRepository extends JpaRepository<DragAndDropAssignment, Long> {

}
