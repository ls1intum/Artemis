package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Spring Data JPA repository for the Exercise entity for Tests.
 */
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
}
