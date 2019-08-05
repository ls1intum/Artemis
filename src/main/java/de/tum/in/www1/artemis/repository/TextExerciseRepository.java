package de.tum.in.www1.artemis.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

/**
 * Spring Data JPA repository for the TextExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextExerciseRepository extends JpaRepository<TextExercise, Long> {

    @Query("SELECT e FROM TextExercise e WHERE e.course.id = :#{#courseId}")
    List<TextExercise> findByCourseId(@Param("courseId") Long courseId);

    List<TextExercise> findByAssessmentTypeAndDueDateIsAfter(AssessmentType assessmentType, ZonedDateTime dueDate);
}
