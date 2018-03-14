package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.QuizExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long> {

    List<QuizExercise> findByCourseId(Long courseId);

    List<QuizExercise> findByIsPlannedToStartAndReleaseDateIsAfter(Boolean plannedToStart, ZonedDateTime earliestReleaseDate);

}
