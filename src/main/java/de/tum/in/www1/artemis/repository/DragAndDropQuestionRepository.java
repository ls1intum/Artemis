package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;

/**
 * Spring Data JPA repository for the DragAndDropQuestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropQuestionRepository extends JpaRepository<DragAndDropQuestion, Long> {

}
