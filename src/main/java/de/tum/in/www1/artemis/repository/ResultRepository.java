package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

/**
 * Spring Data JPA repository for the Result entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {

    List<Result> findByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    List<Result> findAllByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    List<Result> findByParticipationExerciseIdOrderByCompletionDateAsc(Long exerciseId);

    // TODO: cleanup unused queries

    @Query("select distinct r from Result r left join fetch r.feedbacks where r.completionDate = (select max(rr.completionDate) from Result rr where rr.assessmentType = 'AUTOMATIC' and rr.participation.exercise.id = :exerciseId and rr.participation.student.id = r.participation.student.id) and r.participation.exercise.id = :exerciseId and r.participation.student.id IS NOT NULL order by r.completionDate asc")
    List<Result> findLatestAutomaticResultsWithEagerFeedbacksForExercise(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "feedbacks")
    Optional<Result> findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @Query("select r from Result r where r.completionDate = (select min(rr.completionDate) from Result rr where rr.participation.exercise.id = r.participation.exercise.id and rr.participation.student.id = r.participation.student.id and rr.successful = true) and r.participation.exercise.course.id = :courseId and r.successful = true order by r.completionDate asc")
    List<Result> findEarliestSuccessfulResultsForCourse(@Param("courseId") Long courseId);

    Optional<Result> findFirstByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    Optional<Result> findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(Long participationId, boolean rated);

    Optional<Result> findDistinctBySubmissionId(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = "assessor")
    Optional<Result> findDistinctWithAssessorBySubmissionId(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = "feedbacks")
    Optional<Result> findDistinctWithFeedbackBySubmissionId(Long submissionId);

    @Query("select r from Result r left join fetch r.feedbacks where r.id = :resultId")
    Optional<Result> findByIdWithEagerFeedbacks(@Param("resultId") Long id);

    @Query("select r from Result r left join fetch r.submission left join fetch r.feedbacks where r.id = :resultId")
    Optional<Result> findByIdWithEagerSubmissionAndFeedbacks(@Param("resultId") Long id);

    @Query("select r from Result r left join fetch r.feedbacks left join fetch r.assessor where r.id = :resultId")
    Optional<Result> findByIdWithEagerFeedbacksAndAssessor(@Param("resultId") Long id);

    /**
     * Load a result from the database by its id together with the associated submission, the list of feedback items and the assessor.
     *
     * @param resultId the id of the result to load from the database
     * @return an optional containing the result with submission, feedback list and assessor, or an empty optional if no result could be found for the given id
     */
    @EntityGraph(type = LOAD, attributePaths = { "submission", "feedbacks", "assessor" })
    Optional<Result> findWithEagerSubmissionAndFeedbackAndAssessorById(Long resultId);

    Long countByAssessorIsNotNullAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(long courseId, boolean rated);

    Long countByAssessor_IdAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(long assessorId, long courseId, boolean rated);

    List<Result> findAllByParticipation_Exercise_CourseId(Long courseId);

    /**
     * Load a result from the database by its id together with the associated submission and the list of feedback items.
     *
     * @param resultId the id of the result to load from the database
     * @return an optional containing the result with submission and feedback list, or an empty optional if no result could be found for the given id
     */
    @EntityGraph(type = LOAD, attributePaths = { "submission", "feedbacks" })
    Optional<Result> findWithEagerSubmissionAndFeedbackById(long resultId);

    @Query("SELECT COUNT(DISTINCT p) FROM StudentParticipation p left join p.results r WHERE p.exercise.id = :exerciseId AND r.assessor IS NOT NULL AND r.rated = TRUE AND r.completionDate IS NOT NULL AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate)")
    long countNumberOfFinishedAssessmentsForExercise(@Param("exerciseId") Long exerciseId);

    @Query("SELECT COUNT(DISTINCT p) FROM StudentParticipation p left join p.results r WHERE p.exercise.id = :exerciseId AND r.assessor IS NOT NULL AND r.rated = TRUE AND r.submission.submitted = TRUE AND r.completionDate IS NOT NULL AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate) AND NOT EXISTS (select prs from p.results prs where prs.assessor.id = p.student.id)")
    long countNumberOfFinishedAssessmentsForExerciseIgnoreTestRuns(@Param("exerciseId") Long exerciseId);

    /**
     * @param exerciseId
     * @param correctionRound
     * @return the number of completed assessments for the specified correction round of an exam exercise
     */
    @Query("""
               SELECT COUNT(DISTINCT p)
               FROM StudentParticipation p WHERE p.exercise.id = :exerciseId AND
                            (SELECT COUNT(r)
                            FROM Result r
                            WHERE r.assessor IS NOT NULL
                                AND r.rated = TRUE
                                AND r.submission = (select max(id) from p.submissions)
                                AND r.submission.submitted = TRUE
                                AND r.completionDate IS NOT NULL
                                AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate)
                                AND NOT EXISTS (select prs from p.results prs where prs.assessor.id = p.student.id)
                            ) >= (:correctionRound + 1L)
            """)
    long countNumberOfFinishedAssessmentsByCorrectionRoundsAndExerciseIdIgnoreTestRuns(@Param("exerciseId") Long exerciseId, @Param("correctionRound") Long correctionRound);

    @EntityGraph(type = LOAD, attributePaths = { "feedbacks" })
    List<Result> findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(Long exerciseId);

    @Query("SELECT COUNT(DISTINCT p) FROM Participation p left join p.results r WHERE p.exercise.id = :exerciseId AND r.assessor IS NOT NULL AND r.assessmentType IN :types AND r.rated = TRUE AND r.completionDate IS NOT NULL AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate)")
    long countNumberOfAssessmentsByTypeForExerciseBeforeDueDate(@Param("exerciseId") Long exerciseId, @Param("types") List<AssessmentType> types);

    @Query("SELECT COUNT(DISTINCT p) FROM Participation p left join p.results r WHERE p.exercise.id = :exerciseId AND r.assessor IS NOT NULL AND r.assessmentType IN :types AND r.rated = FALSE AND r.completionDate IS NOT NULL AND p.exercise.dueDate IS NOT NULL AND r.submission.submissionDate > p.exercise.dueDate")
    long countNumberOfAssessmentsByTypeForExerciseAfterDueDate(@Param("exerciseId") Long exerciseId, @Param("types") List<AssessmentType> types);

    long countByAssessor_IdAndParticipation_ExerciseIdAndRatedAndCompletionDateIsNotNull(Long tutorId, Long exerciseId, boolean rated);

    /**
     * Checks if a result for the given participation exists.
     *
     * @param participationId the id of the participation to check.
     * @return true if a result for the given participation exists, false otherwise.
     */
    boolean existsByParticipationId(long participationId);

    /**
     * Returns true if there is at least one result for the given exercise.
     * @param exerciseId id of an Exercise.
     * @return true if there is a result, false if not.
     */
    boolean existsByParticipation_ExerciseId(long exerciseId);
}
