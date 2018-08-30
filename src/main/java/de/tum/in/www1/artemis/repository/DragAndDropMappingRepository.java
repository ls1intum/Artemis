package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.DragAndDropMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the DragAndDropMapping entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropMappingRepository extends JpaRepository<DragAndDropMapping, Long> {

}
