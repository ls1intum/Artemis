package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.DragAndDropSubmittedAnswer;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the DragAndDropSubmittedAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropSubmittedAnswerRepository extends JpaRepository<DragAndDropSubmittedAnswer, Long> {

}
