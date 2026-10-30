package de.tum.cit.aet.artemis.exercise.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Spring Data JPA repository for the Exercise entity for Tests.
 */
@Primary
@Lazy
@Repository
public interface ExerciseTestRepository extends ExerciseRepository {

    @EntityGraph(attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    Set<Exercise> findAllExercisesByCourseIdWithEagerParticipation(@Param("courseId") Long courseId);

    @EntityGraph(attributePaths = "categories")
    List<Exercise> findAllWithCategoriesByCourseId(Long courseId);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Exercise e
            SET e.releaseDate = :releaseDate, e.startDate = :startDate, e.dueDate = :dueDate
            """)
    void updateAllExerciseDates(@Param("releaseDate") ZonedDateTime releaseDate, @Param("startDate") ZonedDateTime startDate, @Param("dueDate") ZonedDateTime dueDate);
}
