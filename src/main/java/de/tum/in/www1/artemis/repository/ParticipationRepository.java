package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    List<Participation> findByExerciseId(@Param("exerciseId") Long exerciseId);

    boolean existsByExerciseId(@Param("exerciseId") Long exerciseId);

    long countByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.exercise.course.id = :courseId")
    List<Participation> findByCourseIdWithEagerResults(@Param("courseId") Long courseId);

    @Query("SELECT DISTINCT participation FROM Participation participation LEFT JOIN FETCH participation.results r LEFT JOIN FETCH r.assessor WHERE participation.exercise.course.id = :courseId")
    List<Participation> findByCourseIdWithEagerResultsAndAssessors(@Param("courseId") Long courseId);

    Optional<Participation> findByExerciseIdAndStudentLogin(Long exerciseId, String username);

    Optional<Participation> findByInitializationStateAndExerciseIdAndStudentLogin(InitializationState initializationState, Long exerciseId, String username);

    @Query("select distinct participation from Participation participation left join fetch participation.submissions where participation.exercise.id = :#{#exerciseId} and participation.student.login = :#{#username}")
    Optional<Participation> findByExerciseIdAndStudentLoginWithEagerSubmissions(@Param("exerciseId") Long exerciseId, @Param("username") String username);

    Participation findOneByExerciseIdAndStudentLoginAndInitializationState(Long exerciseId, String username, InitializationState state);

    List<Participation> findByBuildPlanIdAndInitializationState(String buildPlanId, InitializationState state);

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.student.login = :#{#username}")
    List<Participation> findByStudentUsernameWithEagerResults(@Param("username") String username);

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.exercise.id = :#{#exerciseId}")
    List<Participation> findByExerciseIdWithEagerResults(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.exercise.id = :#{#exerciseId} and participation.student.id = :#{#studentId}")
    List<Participation> findByExerciseIdAndStudentIdWithEagerResults(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("select distinct participation from Participation participation left join fetch participation.results par where participation.exercise.id = :#{#exerciseId} and participation.student.id = :#{#studentId} and (par.id = (select max(id) from participation.results) or par.id = null)")
    Optional<Participation> findByExerciseIdAndStudentIdWithLatestResult(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    /**
     * Find all participations of submissions that are submitted and do not already have a manual result. No manual result means that no user has started an assessment for the
     * corresponding submission yet.
     *
     * @param exerciseId the exercise id the participations should belong to
     * @return a list of participations including their submitted submissions that do not have a manual result
     */
    @Query("select distinct participation from Participation participation left join fetch participation.submissions submission left join fetch submission.result result where participation.exercise.id = :#{#exerciseId} and submission.submitted = true and (result is null or result.assessmentType = 'AUTOMATIC')")
    List<Participation> findByExerciseIdWithEagerSubmittedSubmissionsWithoutManualResults(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.id = :#{#participationId}")
    Optional<Participation> findByIdWithEagerResults(@Param("participationId") Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions and results of the participation from the database. Returns an empty Optional if the
     * participation could not be found.
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions and results or an empty Optional
     */
    @EntityGraph(attributePaths = { "submissions", "submissions.result", "results" })
    Optional<Participation> findWithEagerSubmissionsAndResultsById(Long participationId);

    @Query("select distinct participation from Participation participation left join fetch participation.submissions left join fetch participation.results r left join fetch r.assessor where participation.id = :#{#participationId}")
    Optional<Participation> findByIdWithEagerSubmissionsAndEagerResultsAndEagerAssessors(@Param("participationId") Long participationId);

    // TODO: at the moment we don't want to consider online courses due to some legacy programming exercises where the VCS repo does not notify Artemis that there is a new
    // submission). In the future we can deactivate the last part.
    @Query("select distinct participation from Participation participation left join fetch participation.results where participation.buildPlanId is not null and participation.student is not null and participation.exercise.course.onlineCourse = false")
    List<Participation> findAllWithBuildPlanId();

    @Query("SELECT DISTINCT participation FROM Participation participation LEFT JOIN FETCH participation.submissions s LEFT JOIN FETCH s.result r LEFT JOIN FETCH r.assessor WHERE participation.exercise.id = :#{#exerciseId}")
    List<Participation> findAllByExerciseIdWithEagerSubmissionsAndEagerResultsAndEagerAssessor(@Param("exerciseId") long exerciseId);

    @Query("SELECT DISTINCT participation FROM Participation participation LEFT JOIN FETCH participation.exercise e LEFT JOIN FETCH e.course WHERE participation.id = :#{#participationId}")
    Participation findOneByIdWithEagerExerciseAndEagerCourse(@Param("participationId") Long participationId);
}
