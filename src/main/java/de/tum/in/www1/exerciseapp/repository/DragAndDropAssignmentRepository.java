package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.DragAndDropAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the DragAndDropAssignment entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropAssignmentRepository extends JpaRepository<DragAndDropAssignment, Long> {

}
