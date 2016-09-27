package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    Participation findOneByExerciseIdAndStudentLogin(Long exerciseId, String username);

    Participation findOneByExerciseIdAndStudentLoginAndInitializationState(Long exerciseId, String username, ParticipationState state);

    Participation findOneByBuildPlanId(String buildPlanId);

}
