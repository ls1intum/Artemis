package de.tum.in.www1.artemis.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropMapping;

/**
 * Spring Data JPA repository for the DragAndDropMapping entity.
 */
@Profile("core")
@Repository
public interface DragAndDropMappingRepository extends JpaRepository<DragAndDropMapping, Long> {

}
