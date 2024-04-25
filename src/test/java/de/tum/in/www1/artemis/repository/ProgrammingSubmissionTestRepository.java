package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ProgrammingSubmission entity tests.
 */
@Repository
public interface ProgrammingSubmissionTestRepository extends JpaRepository<ProgrammingSubmission, Long> {

    @EntityGraph(type = LOAD, attributePaths = "results")
    @Query("""
                SELECT s
                FROM ProgrammingSubmission s
                WHERE s.participation.id = :participationId
            """)
    List<ProgrammingSubmission> findAllByParticipationIdWithResults(@Param("participationId") Long participationId);

    /**
     * Get the latest legal submission for a participation (ignoring illegal submissions).
     * The type of the submission can also be null, so that the query also returns submissions without a type.
     *
     * @param participationId the id of the participation
     * @return the latest legal submission for the participation
     */
    default Optional<ProgrammingSubmission> findFirstByParticipationIdWithResultsOrderByLegalSubmissionDateDesc(Long participationId) {
        return findFirstByTypeNotAndTypeNotNullAndParticipationIdAndResultsNotNullOrderBySubmissionDateDesc(SubmissionType.ILLEGAL, participationId);
    }

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<ProgrammingSubmission> findFirstByTypeNotAndTypeNotNullAndParticipationIdAndResultsNotNullOrderBySubmissionDateDesc(SubmissionType type, Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<ProgrammingSubmission> findFirstByParticipationIdOrderBySubmissionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "buildLogEntries" })
    Optional<ProgrammingSubmission> findWithEagerBuildLogEntriesById(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.testCase", "results.assessor" })
    Optional<ProgrammingSubmission> findWithEagerResultsFeedbacksTestCasesAssessorById(long submissionId);

    /**
     * Get the programming submission with the given id from the database. The submission is loaded together with exercise it belongs to, its result, the feedback of the result and
     * the assessor of the result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the programming submission with the given id
     */
    @NotNull
    default ProgrammingSubmission findByIdWithResultsFeedbacksAssessorTestCases(long submissionId) {
        return findWithEagerResultsFeedbacksTestCasesAssessorById(submissionId).orElseThrow(() -> new EntityNotFoundException("Programming Submission", submissionId));
    }

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<ProgrammingSubmission> findWithEagerResultsById(Long submissionId);

    @Query("""
            SELECT s
            FROM ProgrammingSubmission s
                LEFT JOIN FETCH s.results
            WHERE (s.type <> de.tum.in.www1.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
                AND s.participation.id = :participationId
                AND s.id = (
                    SELECT MAX(s2.id)
                    FROM ProgrammingSubmission s2
                    WHERE s2.participation.id = :participationId
                        AND (s2.type <> de.tum.in.www1.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s2.type IS NULL))
            """)
    Optional<ProgrammingSubmission> findFirstByParticipationIdOrderByLegalSubmissionDateDesc(@Param("participationId") Long participationId);
}
