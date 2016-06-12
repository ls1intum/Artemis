package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Participation;

import de.tum.in.www1.exerciseapp.domain.User;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
public interface ParticipationRepository extends JpaRepository<Participation,Long> {

    @Query("select participation from Participation participation where participation.student.login = ?#{principal.username}")
    List<Participation> findByStudentIsCurrentUser();

    @Query("select participation from Participation participation where participation.exercise.id = :exerciseId and participation.student.login = ?#{principal.username}")
    Participation findOneByExerciseIdAndStudentIsCurrentUser(@Param("exerciseId") Long exerciseId);

    Participation findOneByExerciseBaseProjectKeyAndStudentLogin(String baseProjectKey, String username);

}
