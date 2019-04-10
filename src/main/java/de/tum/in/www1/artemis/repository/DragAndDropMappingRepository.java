package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropMapping;

/**
 * Spring Data JPA repository for the DragAndDropMapping entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropMappingRepository extends JpaRepository<DragAndDropMapping, Long> {

}
