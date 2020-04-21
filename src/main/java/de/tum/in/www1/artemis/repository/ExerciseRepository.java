package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;

/**
 * Spring Data JPA repository for the Exercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findAllByCourseId(@Param("courseId") Long courseId);

    /**
     * Select Exercise for Course ID WHERE there does exist an LtiOutcomeUrl for the current user (-> user has started exercise once using LTI)
     * @param courseId the id of the course
     * @param login the login of the corresponding user
     * @return list of exercises
     */
    @Query("SELECT e FROM Exercise e WHERE e.course.id = :#{#courseId} AND EXISTS (SELECT l FROM LtiOutcomeUrl l WHERE e = l.exercise AND l.user.login = :#{#login})")
    List<Exercise> findAllByCourseIdWhereLtiOutcomeUrlExists(@Param("courseId") Long courseId, @Param("login") String login);

    @Query("SELECT DISTINCT c FROM Exercise e JOIN e.categories c WHERE e.course.id = :#{#courseId}")
    Set<String> findAllCategoryNames(@Param("courseId") Long courseId);

    @Query("SELECT DISTINCT exercise FROM Exercise exercise LEFT JOIN FETCH exercise.studentParticipations WHERE exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithEagerParticipations(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT exercise FROM Exercise exercise LEFT JOIN FETCH exercise.categories WHERE exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithEagerCategories(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT exercise FROM Exercise exercise LEFT JOIN FETCH exercise.exampleSubmissions WHERE exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithEagerExampleSubmissions(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT exercise FROM Exercise exercise LEFT JOIN FETCH exercise.exerciseHints LEFT JOIN FETCH exercise.studentQuestions LEFT JOIN FETCH exercise.categories WHERE exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithDetailsForStudent(@Param("exerciseId") Long exerciseId);
}
