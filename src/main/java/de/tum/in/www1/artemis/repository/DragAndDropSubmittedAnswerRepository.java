package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.DragAndDropSubmittedAnswer;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the DragAndDropSubmittedAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropSubmittedAnswerRepository extends JpaRepository<DragAndDropSubmittedAnswer, Long> {

}
