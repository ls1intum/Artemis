package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.DragAndDropQuestion;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the DragAndDropQuestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropQuestionRepository extends JpaRepository<DragAndDropQuestion, Long> {

}
