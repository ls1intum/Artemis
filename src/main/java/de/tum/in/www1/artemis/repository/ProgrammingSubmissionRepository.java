package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ProgrammingSubmission entity.
 */
@Repository
public interface ProgrammingSubmissionRepository extends JpaRepository<ProgrammingSubmission, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "results.feedbacks" })
    ProgrammingSubmission findFirstByParticipationIdAndCommitHashOrderByIdDesc(Long participationId, String commitHash);

    @Query("""
            SELECT s FROM ProgrammingSubmission s
            LEFT JOIN FETCH s.results
            WHERE (s.type <> 'ILLEGAL' or s.type is null)
            AND s.participation.id = :#{#participationId}
            AND s.id = (SELECT max(s2.id) FROM ProgrammingSubmission s2 WHERE s2.participation.id = :#{#participationId} AND (s2.type <> 'ILLEGAL' or s2.type is null))
            """)
    Optional<ProgrammingSubmission> findFirstByParticipationIdOrderByLegalSubmissionDateDesc(@Param("participationId") Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<ProgrammingSubmission> findFirstByParticipationIdOrderBySubmissionDateDesc(Long participationId);

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
    @EntityGraph(type = LOAD, attributePaths = "results")
    @Query("select s from ProgrammingSubmission s left join s.participation p left join p.exercise e where p.id = :#{#participationId} and (s.type = 'INSTRUCTOR' or s.type = 'TEST' or e.dueDate is null or s.submissionDate <= e.dueDate) order by s.submissionDate desc")
    List<ProgrammingSubmission> findGradedByParticipationIdOrderBySubmissionDateDesc(@Param("participationId") Long participationId, Pageable pageable);

    @Query("""
            select s from ProgrammingSubmission s
            where s.participation.id = :#{#participationId} and (s.type <> 'ILLEGAL' or s.type is null)
            order by s.submissionDate desc
            """)
    List<ProgrammingSubmission> findLatestLegalSubmissionForParticipation(@Param("participationId") Long participationId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<ProgrammingSubmission> findWithEagerResultsById(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.assessor" })
    Optional<ProgrammingSubmission> findWithEagerResultsFeedbacksAssessorById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "buildLogEntries" })
    Optional<ProgrammingSubmission> findWithEagerBuildLogEntriesById(Long submissionId);

    @Query("select s from ProgrammingSubmission s left join fetch s.results r where r.id = :#{#resultId}")
    Optional<ProgrammingSubmission> findByResultId(@Param("resultId") Long resultId);

    @EntityGraph(type = LOAD, attributePaths = "results")
    @Query("select s from ProgrammingSubmission s where s.participation.id = :#{#participationId}")
    List<ProgrammingSubmission> findAllByParticipationIdWithResults(@Param("participationId") Long participationId);

    /**
     * Get the programming submission with the given id from the database. The submission is loaded together with exercise it belongs to, its result, the feedback of the result and the assessor of the
     * result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the programming submission with the given id
     */
    @NotNull
    default ProgrammingSubmission findByIdWithResultsFeedbacksAssessor(long submissionId) {
        return findWithEagerResultsFeedbacksAssessorById(submissionId).orElseThrow(() -> new EntityNotFoundException("Programming Submission", submissionId));
    }

    @NotNull
    default ProgrammingSubmission findByResultIdElseThrow(Long resultId) {
        return findByResultId(resultId).orElseThrow(() -> new EntityNotFoundException("Programming Submission for Result", resultId));
    }
}
