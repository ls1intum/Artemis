package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
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

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.exercise.course.id = :courseId")
    List<Participation> findByCourseIdWithEagerResults(@Param("courseId") Long courseId);

    Participation findOneByExerciseIdAndStudentLogin(Long exerciseId, String username);

    Participation findOneByExerciseIdAndStudentLoginAndInitializationState(Long exerciseId, String username, InitializationState state);

    List<Participation> findByBuildPlanIdAndInitializationState(String buildPlanId, InitializationState state);

    @Query("select participation from Participation participation where participation.student.login = ?#{principal.username}")
    List<Participation> findByStudentIsCurrentUser();

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.student.login = :#{#username}")
    List<Participation> findByStudentUsernameWithEagerResults(@Param("username") String username);

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.exercise.id = :#{#exerciseId}")
    List<Participation> findByExerciseIdWithEagerResults(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from Participation participation left join fetch participation.submissions where participation.exercise.id = :#{#exerciseId}")
    List<Participation> findByExerciseIdWithEagerSubmissions(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.id = :#{#participationId}")
    Participation findByIdWithEagerResults(@Param("participationId") Long participationId);

    @Query("select distinct participation from Participation participation left join fetch participation.submissions where participation.id = :#{#participationId}")
    Participation findByIdWithEagerSubmissions(@Param("participationId") Long participationId);

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.buildPlanId is not null and participation.exercise.dueDate is not null and participation.exercise.dueDate < current_date")
    List<Participation> findAllExpiredWithBuildPlanId();
}
