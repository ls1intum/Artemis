package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragItem;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the DragItem entity.
 */
@Repository
public interface DragItemRepository extends JpaRepository<DragItem, Long> {

    @Query("""
            SELECT dragItem
            FROM DragItem dragItem
                LEFT JOIN FETCH dragItem.question q
                LEFT JOIN FETCH q.exercise e
                LEFT JOIN FETCH e.course
                LEFT JOIN FETCH e.exerciseGroup
            WHERE dragItem.id = :#{#dragItemId}
            """)
    Optional<DragItem> findDragItemByIdWithEagerQuestionAndExerciseAndCourse(@Param("dragItemId") Long dragItemId);

    default DragItem findDragItemByIdWithEagerQuestionAndExerciseAndCourseOrThrow(Long dragItemId) {
        return findDragItemByIdWithEagerQuestionAndExerciseAndCourse(dragItemId).orElseThrow(() -> new EntityNotFoundException("DragItem", dragItemId));
    }

}
