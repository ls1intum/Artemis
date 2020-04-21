package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;

/**
 * Spring Data JPA repository for the ModelingExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingExerciseRepository extends JpaRepository<ModelingExercise, Long> {

    @Query("SELECT e FROM ModelingExercise e WHERE e.course.id = :#{#courseId}")
    List<ModelingExercise> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT DISTINCT modelingExercise FROM ModelingExercise modelingExercise LEFT JOIN FETCH modelingExercise.exampleSubmissions WHERE modelingExercise.id = :#{#exerciseId}")
    Optional<ModelingExercise> findByIdWithEagerExampleSubmissions(@Param("exerciseId") Long exerciseId);

}
