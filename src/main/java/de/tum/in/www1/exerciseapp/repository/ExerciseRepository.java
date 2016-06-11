package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for the Exercise entity.
 */
@SuppressWarnings("unused")
public interface ExerciseRepository extends JpaRepository<Exercise,Long> {

    @Query(value = "select e from Exercise e where e.course.id = :courseId and e.releaseDate > CURRENT_TIMESTAMP")
    Page<Exercise> findByCourseId(@Param("courseId") Long courseId, Pageable pageable);

}
