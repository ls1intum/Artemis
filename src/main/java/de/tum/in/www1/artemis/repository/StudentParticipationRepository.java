package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StudentParticipationRepository extends JpaRepository<StudentParticipation, Long> {

    List<StudentParticipation> findAllByExerciseId(@Param("exerciseId") Long exerciseId);

    boolean existsByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.results r WHERE participation.exercise.course.id = :#{#courseId} AND (r.rated IS NULL OR r.rated = true)")
    List<StudentParticipation> findAllWithEagerRatedResultsByCourseId(@Param("courseId") Long courseId);

    Optional<StudentParticipation> findByExerciseIdAndStudentLogin(Long exerciseId, String username);

    Optional<StudentParticipation> findByExerciseIdAndTeamId(Long exerciseId, Long teamId);

    List<StudentParticipation> findAllByTeamId(Long teamId);

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<StudentParticipation> findWithEagerResultsByExerciseIdAndStudentLogin(Long exerciseId, String username);

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<StudentParticipation> findWithEagerResultsByExerciseIdAndTeamId(Long exerciseId, Long teamId);

    @EntityGraph(type = LOAD, attributePaths = "submissions")
    Optional<StudentParticipation> findWithEagerSubmissionsByExerciseIdAndStudentLogin(Long exerciseId, String username);

    @EntityGraph(type = LOAD, attributePaths = "submissions")
    Optional<StudentParticipation> findWithEagerSubmissionsByExerciseIdAndTeamId(Long exerciseId, Long teamId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.submissions s LEFT JOIN FETCH s.result WHERE participation.exercise.id = :#{#exerciseId}")
    List<StudentParticipation> findAllWithEagerSubmissionsResultByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.submissions s left join fetch s.result r LEFT JOIN FETCH r.assessor WHERE participation.exercise.id = :#{#exerciseId}")
    List<StudentParticipation> findAllWithEagerSubmissionsResultAssessorByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Get all participations for an exercise with each latest result (determined by id).
     * If there is no latest result (= no result at all), the participation will still be included in the returned ResultSet, but will have an empty Result array.
     *
     * @param exerciseId Exercise id.
     * @return participations for exercise.
     */
    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.results result WHERE participation.exercise.id = :#{#exerciseId} AND (result.id = (SELECT MAX(id) FROM participation.results) OR result IS NULL)")
    List<StudentParticipation> findAllWithLatestResultByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.results LEFT JOIN FETCH participation.submissions WHERE participation.exercise.id = :#{#exerciseId} AND participation.student.id = :#{#studentId}")
    List<StudentParticipation> findAllWithEagerResultsAndSubmissionsByExerciseIdAndStudentId(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.results LEFT JOIN FETCH participation.submissions WHERE participation.exercise.id = :#{#exerciseId} AND participation.team.id = :#{#teamId}")
    List<StudentParticipation> findAllWithEagerResultsAndSubmissionsByExerciseIdAndTeamId(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.results as par LEFT JOIN FETCH par.feedbacks WHERE participation.exercise.id = :#{#exerciseId} AND participation.student.id = :#{#studentId} AND (par.id = (SELECT MAX(id) FROM participation.results) OR par.id = NULL)")
    Optional<StudentParticipation> findWithLatestResultByExerciseIdAndStudentId(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.results as par LEFT JOIN FETCH par.feedbacks WHERE participation.exercise.id = :#{#exerciseId} AND participation.team.id = :#{#teamId} AND (par.id = (SELECT MAX(id) FROM participation.results) OR par.id = NULL)")
    Optional<StudentParticipation> findWithLatestResultByExerciseIdAndTeamId(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    /**
     * Find all participations of submissions that are submitted and do not already have a manual result. No manual result means that no user has started an assessment for the
     * corresponding submission yet.
     *
     * If a student can have multiple submissions per exercise type, the latest submission (by id) will be returned.
     *
     * @param exerciseId the exercise id the participations should belong to
     * @return a list of participations including their submitted submissions that do not have a manual result
     */
    @Query("SELECT DISTINCT participation FROM Participation participation LEFT JOIN FETCH participation.submissions submission LEFT JOIN FETCH submission.result result WHERE participation.exercise.id = :#{#exerciseId} AND NOT EXISTS (SELECT prs FROM participation.results prs where prs.assessmentType = 'MANUAL') and submission.submitted = true and submission.id = (select max(id) from participation.submissions)")
    List<StudentParticipation> findAllWithLatestSubmissionWithoutManualResultsByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.results WHERE participation.id = :#{#participationId}")
    Optional<StudentParticipation> findWithEagerResultsById(@Param("participationId") Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions and results of the participation from the database. Returns an empty Optional if the
     * participation could not be found.
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions and results or an empty Optional
     */
    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.result", "results", "team.students" })
    Optional<StudentParticipation> findWithEagerSubmissionsAndResultsById(Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions and results of the participation from the database.
     * Further, load the exercise and its course. Returns an empty Optional if the participation could not be found.
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions, results, exercise and course or an empty Optional
     */
    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.result", "submissions.result.feedbacks", "results", "exercise", "exercise.course", "team.students" })
    Optional<StudentParticipation> findWithEagerSubmissionsAndResultsAndExerciseAndCourseById(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "results", "results.assessor" })
    Optional<StudentParticipation> findWithEagerSubmissionsAndResultsAssessorsById(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.result", "submissions.result.assessor" })
    List<StudentParticipation> findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(long exerciseId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.exercise e LEFT JOIN FETCH e.course WHERE participation.id = :#{#participationId}")
    StudentParticipation findWithEagerExerciseAndEagerCourseById(@Param("participationId") Long participationId);

    @Query("SELECT DISTINCT participation FROM StudentParticipation participation LEFT JOIN FETCH participation.results LEFT JOIN FETCH participation.exercise e LEFT JOIN FETCH e.course WHERE participation.id = :#{#participationId}")
    StudentParticipation findWithEagerResultsAndExerciseAndEagerCourseById(@Param("participationId") Long participationId);

    @Query("SELECT DISTINCT p FROM StudentParticipation p LEFT JOIN p.team t LEFT JOIN t.students teamStudent LEFT JOIN FETCH p.submissions s LEFT JOIN FETCH s.result r WHERE p.exercise IN :#{#exercises} AND (p.student.id = :#{#studentId} OR teamStudent.id = :#{#studentId})")
    List<StudentParticipation> findAllWithEagerSubmissionsResultByStudentIdAndExercise(@Param("studentId") Long studentId, @Param("exercises") Set<Exercise> exercises);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.result", "submissions.result.assessor" })
    @Query("SELECT DISTINCT p FROM StudentParticipation p LEFT JOIN FETCH p.submissions s WHERE p.exercise.id = :#{#exerciseId} AND (s.result.assessor.id = :#{#assessorId} AND s.id = (SELECT MAX(id) FROM p.submissions) OR s.id = NULL)")
    List<StudentParticipation> findAllWithLatestSubmissionByExerciseIdAndAssessorId(@Param("exerciseId") Long exerciseId, @Param("assessorId") Long assessorId);

    /**
     * Count the number of submissions for each participation in a given exercise.
     *
     * @param exerciseId the id of the exercise for which to consider participations
     * @return Tuples of participation ids and number of submissions per participation
     */
    @Query("SELECT participation.id, COUNT(submissions) FROM StudentParticipation participation LEFT JOIN participation.submissions submissions WHERE participation.exercise.id = :#{#exerciseId} GROUP BY participation.id")
    List<long[]> countSubmissionsPerParticipationByExerciseId(@Param("exerciseId") long exerciseId);
}
