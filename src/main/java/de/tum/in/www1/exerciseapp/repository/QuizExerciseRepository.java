package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.QuizExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long> {

    @Query("SELECT distinct e FROM QuizExercise e WHERE e.course.id = :#{#courseId}")
    List<QuizExercise> findByCourseId(@Param("courseId") Long courseId);

    @Query("select e from QuizExercise e left join fetch e.questions left join fetch e.quizPointStatistic where e.id = :#{#exerciseId}")
    QuizExercise findOneByIdWithEagerQuestionsAndStatistics(@Param("exerciseId") Long exerciseId);

}
