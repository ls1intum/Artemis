package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.DragAndDropStatistic;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the DragAndDropStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropStatisticRepository extends JpaRepository<DragAndDropStatistic,Long> {
    
}
