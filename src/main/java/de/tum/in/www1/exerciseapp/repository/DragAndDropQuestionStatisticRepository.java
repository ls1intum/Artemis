package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.DragAndDropQuestionStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the DragAndDropQuestionStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DragAndDropQuestionStatisticRepository extends JpaRepository<DragAndDropQuestionStatistic,Long> {

}
