package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the Exercise entity.
 */
@SuppressWarnings("unused")
public interface ExerciseRepository extends JpaRepository<Exercise,Long> {

    Page<Exercise> findByCourseId(Long courseId, Pageable pageable);

}
