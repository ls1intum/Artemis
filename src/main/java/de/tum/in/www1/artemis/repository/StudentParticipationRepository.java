package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.StudentParticipation;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StudentParticipationRepository extends JpaRepository<StudentParticipation, Long> {

    List<StudentParticipation> findByExerciseId(@Param("exerciseId") Long exerciseId);

    boolean existsByExerciseId(@Param("exerciseId") Long exerciseId);

    long countByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results where participation.exercise.course.id = :courseId")
    List<StudentParticipation> findByCourseIdWithEagerResults(@Param("courseId") Long courseId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.results r LEFT JOIN FETCH r.assessor WHERE participation.exercise.course.id = :courseId")
    List<StudentParticipation> findByCourseIdWithEagerResultsAndAssessors(@Param("courseId") Long courseId);

    Optional<StudentParticipation> findByExerciseIdAndStudentLogin(Long exerciseId, String username);

    Optional<StudentParticipation> findByInitializationStateAndExerciseIdAndStudentLogin(InitializationState initializationState, Long exerciseId, String username);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.submissions where participation.exercise.id = :#{#exerciseId} and participation.student.login = :#{#username}")
    Optional<StudentParticipation> findByExerciseIdAndStudentLoginWithEagerSubmissions(@Param("exerciseId") Long exerciseId, @Param("username") String username);

    StudentParticipation findOneByExerciseIdAndStudentLoginAndInitializationState(Long exerciseId, String username, InitializationState state);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results where participation.student.login = :#{#username}")
    List<StudentParticipation> findByStudentUsernameWithEagerResults(@Param("username") String username);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results where participation.exercise.id = :#{#exerciseId}")
    List<StudentParticipation> findByExerciseIdWithEagerResults(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results where participation.exercise.id = :#{#exerciseId} and participation.student.id = :#{#studentId}")
    List<StudentParticipation> findByExerciseIdAndStudentIdWithEagerResults(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results par where participation.exercise.id = :#{#exerciseId} and participation.student.id = :#{#studentId} and (par.id = (select max(id) from participation.results) or par.id = null)")
    Optional<StudentParticipation> findByExerciseIdAndStudentIdWithLatestResult(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.submissions submission left join fetch submission.result result where participation.exercise.id = :#{#exerciseId} and submission.submitted = true and result is null")
    List<StudentParticipation> findByExerciseIdWithEagerSubmittedSubmissionsWithoutResults(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results where participation.id = :#{#participationId}")
    Optional<StudentParticipation> findByIdWithEagerResults(@Param("participationId") Long participationId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.submissions where participation.id = :#{#participationId}")
    Optional<StudentParticipation> findByIdWithEagerSubmissions(@Param("participationId") Long participationId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.submissions left join fetch participation.results r left join fetch r.assessor where participation.id = :#{#participationId}")
    Optional<StudentParticipation> findByIdWithEagerSubmissionsAndEagerResultsAndEagerAssessors(@Param("participationId") Long participationId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.submissions s LEFT JOIN FETCH s.result r LEFT JOIN FETCH r.assessor WHERE participation.exercise.id = :#{#exerciseId}")
    List<StudentParticipation> findAllByExerciseIdWithEagerSubmissionsAndEagerResultsAndEagerAssessor(@Param("exerciseId") long exerciseId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.exercise e LEFT JOIN FETCH e.course WHERE participation.id = :#{#participationId}")
    StudentParticipation findOneByIdWithEagerExerciseAndEagerCourse(@Param("participationId") Long participationId);
}
