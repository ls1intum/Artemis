package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;

/**
 * Spring Data JPA repository for the ProgrammingSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingSubmissionRepository extends JpaRepository<ProgrammingSubmission, Long> {

    @EntityGraph(attributePaths = { "result.feedbacks" })
    ProgrammingSubmission findFirstByParticipationIdAndCommitHash(Long participationId, String commitHash);

    @EntityGraph(attributePaths = "result")
    Optional<ProgrammingSubmission> findFirstByParticipationIdOrderBySubmissionDateDesc(Long participationId);

    @EntityGraph(attributePaths = { "result.feedbacks" })
    List<ProgrammingSubmission> findByParticipationIdAndResultIsNullOrderBySubmissionDateDesc(Long participationId);

    @EntityGraph(attributePaths = "result")
    @Query("select distinct s from Submission s where s.id = :#{#submissionId}")
    ProgrammingSubmission findByIdWithEagerResult(@Param("submissionId") Long submissionId);

    Optional<ProgrammingSubmission> findByResultId(long resultId);

    /**
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("SELECT COUNT (DISTINCT submission) FROM ProgrammingSubmission submission WHERE submission.participation.exercise.id = :#{#exerciseId} AND submission.submitted = TRUE AND (submission.submissionDate < submission.participation.exercise.dueDate OR submission.participation.exercise.dueDate IS NULL)")
    long countByExerciseIdSubmittedBeforeDueDate(@Param("exerciseId") Long exerciseId);
}
