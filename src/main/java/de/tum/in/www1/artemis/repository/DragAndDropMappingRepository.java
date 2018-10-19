package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.DragAndDropMapping;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the DragAndDropMapping entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropMappingRepository extends JpaRepository<DragAndDropMapping, Long> {

}
