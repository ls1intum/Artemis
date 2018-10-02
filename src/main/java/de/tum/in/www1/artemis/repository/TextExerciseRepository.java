package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.TextExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * Spring Data JPA repository for the TextExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextExerciseRepository extends JpaRepository<TextExercise, Long> {

    @Query("SELECT e FROM TextExercise e WHERE e.course.id = :#{#courseId}")
    List<TextExercise> findByCourseId(@Param("courseId") Long courseId);
}
