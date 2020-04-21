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

    // TODO: need to clarify line 25 and line 28 methods
    List<Result> findByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    List<Result> findAllByParticipationIdOrderByCompletionDateDesc(Long participationId);

    List<Result> findAllByParticipationIdAndRatedOrderByCompletionDateDesc(Long participationId, boolean rated);

    List<Result> findAllByParticipationExerciseIdOrderByCompletionDateAsc(Long exerciseId);

    // TODO: cleanup unused queries

    @Query("SELECT r FROM Result r WHERE r.completionDate = (SELECT MAX(rr.completionDate) FROM Result rr WHERE rr.participation.exercise.id = :exerciseId AND rr.participation.student.id = r.participation.student.id) AND r.participation.exercise.id = :exerciseId ORDER BY r.completionDate ASC")
    List<Result> findAllLatestResultsForExercise(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "feedbacks")
    Optional<Result> findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @Query("SELECT r FROM Result r WHERE r.completionDate = (SELECT MIN(rr.completionDate) FROM Result rr WHERE rr.participation.exercise.id = r.participation.exercise.id AND rr.participation.student.id = r.participation.student.id AND rr.successful = true) AND r.participation.exercise.course.id = :courseId AND r.successful = true ORDER BY r.completionDate ASC")
    List<Result> findAllEarliestSuccessfulResultsForCourse(@Param("courseId") Long courseId);

    Optional<Result> findFirstByParticipationIdOrderByCompletionDateDesc(Long participationId);

    Optional<Result> findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(Long participationId, boolean rated);

    Optional<Result> findDistinctBySubmissionId(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = "assessor")
    Optional<Result> findDistinctWithAssessorBySubmissionId(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = "feedbacks")
    Optional<Result> findDistinctWithFeedbackBySubmissionId(Long submissionId);

    List<Result> findAllByParticipationExerciseIdAndAssessorId(Long exerciseId, Long assessorId);

    @Query("SELECT r FROM Result r LEFT JOIN FETCH r.feedbacks WHERE r.id = :resultId")
    Optional<Result> findByIdWithEagerFeedbacks(@Param("resultId") Long id);

    @Query("SELECT r FROM Result r LEFT JOIN FETCH r.feedbacks LEFT JOIN FETCH r.assessor WHERE r.id = :resultId")
    Optional<Result> findByIdWithEagerFeedbacksAndAssessor(@Param("resultId") Long id);

    /**
     * Load a result from the database by its id together with the associated submission, the list of feedback items and the assessor.
     *
     * @param resultId the id of the result to load from the database
     * @return an optional containing the result with submission, feedback list and assessor, or an empty optional if no result could be found for the given id
     */
    @EntityGraph(type = LOAD, attributePaths = { "submission", "feedbacks", "assessor" })
    Optional<Result> findWithEagerSubmissionAndFeedbackAndAssessorById(Long resultId);

    Long countByAssessorIsNotNullAndParticipationExerciseCourseIdAndRatedAndCompletionDateIsNotNull(long courseId, boolean rated);

    Long countByAssessorIdAndParticipationExerciseCourseIdAndRatedAndCompletionDateIsNotNull(long assessorId, long courseId, boolean rated);

    List<Result> findAllByParticipationExerciseCourseId(Long courseId);

    /**
     * Load a result from the database by its id together with the associated submission and the list of feedback items.
     *
     * @param resultId the id of the result to load from the database
     * @return an optional containing the result with submission and feedback list, or an empty optional if no result could be found for the given id
     */
    @EntityGraph(type = LOAD, attributePaths = { "submission", "feedbacks" })
    Optional<Result> findWithEagerSubmissionAndFeedbackById(long resultId);

    @Query(value = "SELECT COUNT(DISTINCT p) FROM Participation p LEFT JOIN p.results r WHERE p.exercise.id = :exerciseId AND r.assessor IS NOT NULL AND r.rated = TRUE AND r.completionDate IS NOT NULL")
    long countNumberOfFinishedAssessmentsForExercise(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "feedbacks" })
    List<Result> findAllWithEagerFeedbackByAssessorIsNotNullAndParticipationExerciseIdAndCompletionDateIsNotNull(Long exerciseId);

    long countByAssessorIsNotNullAndParticipationExerciseIdAndRatedAndAssessmentTypeInAndCompletionDateIsNotNull(long exerciseId, boolean rated,
            List<AssessmentType> assessmentType);

    long countByAssessorIdAndParticipationExerciseIdAndRatedAndCompletionDateIsNotNull(Long tutorId, Long exerciseId, boolean rated);

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
    boolean existsByParticipationExerciseId(long exerciseId);
}
