package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.QuizExercise;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long> {

    @Query("SELECT distinct e FROM QuizExercise e left join fetch e.questions WHERE e.course.id = :#{#courseId}")
    List<QuizExercise> findByCourseId(@Param("courseId") Long courseId);

    @Query("select distinct quiz_exercise from QuizExercise quiz_exercise left join fetch quiz_exercise.questions")
    List<QuizExercise> findAllWithEagerRelationships();

    @Query("select quiz_exercise from QuizExercise quiz_exercise left join fetch quiz_exercise.questions where quiz_exercise.id =:id")
    QuizExercise findOneWithEagerRelationships(@Param("id") Long id);

}
