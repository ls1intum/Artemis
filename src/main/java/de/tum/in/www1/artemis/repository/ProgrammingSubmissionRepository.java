package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;

/**
 * Spring Data JPA repository for the ProgrammingSubmission entity.
 */
@Repository
public interface ProgrammingSubmissionRepository extends JpaRepository<ProgrammingSubmission, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "result.feedbacks" })
    ProgrammingSubmission findByParticipationIdAndCommitHash(Long participationId, String commitHash);

    @EntityGraph(type = LOAD, attributePaths = "result")
    Optional<ProgrammingSubmission> findByParticipationIdOrderBySubmissionDateDesc(Long participationId);

    /**
     * Provide a list of graded submissions. To be graded a submission must:
     * - be of type 'INSTRUCTOR' or 'TEST'
     * - have a submission date before the exercise due date
     * - or related to an exercise without a due date
     *
     * @param participationId to which the submissions belong.
     * @param pageable Pageable
     * @return ProgrammingSubmission list (can be empty!)
     */
    @EntityGraph(type = LOAD, attributePaths = "result")
    @Query("SELECT s FROM ProgrammingSubmission s LEFT JOIN s.participation p LEFT JOIN p.exercise e WHERE p.id = :#{#participationId} AND (s.type = 'INSTRUCTOR' OR s.type = 'TEST' OR e.dueDate IS NULL OR s.submissionDate <= e.dueDate) ORDER BY s.submissionDate DESC")
    List<ProgrammingSubmission> findGradedByParticipationIdOrderBySubmissionDateDesc(@Param("participationId") Long participationId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "result.feedbacks" })
    List<ProgrammingSubmission> findByParticipationIdAndResultIsNullOrderBySubmissionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "result")
    Optional<ProgrammingSubmission> findWithEagerResultById(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "result", "result.feedbacks", "result.assessor" })
    Optional<ProgrammingSubmission> findWithEagerResultAssessorFeedbackById(long submissionId);

    Optional<ProgrammingSubmission> findByResultId(long resultId);
}
