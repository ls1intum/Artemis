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

    @EntityGraph(type = LOAD, attributePaths = { "results.feedbacks" })
    ProgrammingSubmission findFirstByParticipationIdAndCommitHash(Long participationId, String commitHash);

    @Query("""
            select s from ProgrammingSubmission s
            left join fetch s.results
            where s.type <> ('ILLEGAL')
            order by s.submissionDate desc
            """)
    Optional<ProgrammingSubmission> findFirstByParticipationIdOrderByLegalSubmissionDateDesc(Long participationId);

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
            where s.participation.id = :#{#participationId} and s.type <> ('ILLEGAL')
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
}
