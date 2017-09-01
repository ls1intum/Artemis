package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.DragAndDropQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the DragAndDropQuestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropQuestionRepository extends JpaRepository<DragAndDropQuestion, Long> {

}
