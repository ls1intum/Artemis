package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    List<Participation> findByExerciseId(@Param("exerciseId") Long exerciseId);

    long countByExerciseId(@Param("exerciseId") Long exerciseId);

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

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.exercise.id = :#{#exerciseId} and participation.student.id = :#{#studentId}")
    List<Participation> findByExerciseIdAndStudentIdWithEagerResults(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("select distinct participation from Participation participation left join fetch participation.submissions where participation.exercise.id = :#{#exerciseId}")
    List<Participation> findByExerciseIdWithEagerSubmissions(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.id = :#{#participationId}")
    Optional<Participation> findByIdWithEagerResults(@Param("participationId") Long participationId);

    @Query("select distinct participation from Participation participation left join fetch participation.submissions where participation.id = :#{#participationId}")
    Optional<Participation> findByIdWithEagerSubmissions(@Param("participationId") Long participationId);

    @Query("select distinct participation from Participation participation left join fetch participation.submissions left join fetch participation.results r left join fetch r.assessor where participation.id = :#{#participationId}")
    Optional<Participation> findByIdWithEagerSubmissionsAndEagerResultsAndEagerAssessors(@Param("participationId") Long participationId);

    //TODO: at the moment we don't want to consider online courses due to some legacy programming exercises where the VCS repo does not notify Artemis that there is a new submission). In the future we can deactivate the last part.
    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.buildPlanId is not null and participation.exercise.course.onlineCourse = false")
    List<Participation> findAllWithBuildPlanId();

    @Query("SELECT DISTINCT participation FROM Participation participation " +
        "LEFT JOIN FETCH participation.submissions s " +
        "LEFT JOIN FETCH s.result r " +
        "LEFT JOIN FETCH r.assessor " +
        "WHERE participation.exercise.id = :#{#exerciseId} AND s.submitted = :#{#submittedOnly}")
    List<Participation> findAllByExerciseIdWithEagerSubmissionsAndEagerResultsAndEagerAssessor(long exerciseId, boolean submittedOnly);
}
