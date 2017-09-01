package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    List<Participation> findByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("select p from Participation p where p.exercise.course.id = :courseId")
    List<Participation> findByCourseId(@Param("courseId") Long courseId);

    Participation findOneByExerciseIdAndStudentLogin(Long exerciseId, String username);

    Participation findOneByExerciseIdAndStudentLoginAndInitializationState(Long exerciseId, String username, ParticipationState state);

    Participation findOneByBuildPlanId(String buildPlanId);

    @Query("select participation from Participation participation where participation.student.login = ?#{principal.username}")
    List<Participation> findByStudentIsCurrentUser();

}
