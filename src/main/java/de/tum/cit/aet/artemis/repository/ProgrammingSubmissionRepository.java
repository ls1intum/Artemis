package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.service.dto.ProgrammingSubmissionIdAndSubmissionDateDTO;

/**
 * Spring Data JPA repository for the ProgrammingSubmission entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingSubmissionRepository extends ArtemisJpaRepository<ProgrammingSubmission, Long> {

    @Query("""
            SELECT s
            FROM ProgrammingSubmission s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH s.participation p
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE p.id = :participationId
                AND s.commitHash = :commitHash
            ORDER BY s.id DESC
            """)
    List<ProgrammingSubmission> findByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(@Param("participationId") long participationId,
            @Param("commitHash") String commitHash);

    default ProgrammingSubmission findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(long participationId, String commitHash) {
        return findByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(participationId, commitHash).stream().findFirst().orElse(null);
    }

    @Query(value = """
            SELECT new de.tum.cit.aet.artemis.service.dto.ProgrammingSubmissionIdAndSubmissionDateDTO(ps.id, ps.submissionDate)
            FROM ProgrammingSubmission ps
            WHERE ps.participation.id = :participationId ORDER BY ps.submissionDate DESC
            """)
    List<ProgrammingSubmissionIdAndSubmissionDateDTO> findFirstIdByParticipationIdOrderBySubmissionDateDesc(@Param("participationId") long participationId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "results" })
    Optional<ProgrammingSubmission> findProgrammingSubmissionWithResultsById(long programmingSubmissionId);

    /**
     * Finds the first programming submission by participation ID, including its results, ordered by submission date in descending order. To avoid in-memory paging by retrieving
     * the first submission directly from the database.
     *
     * @param programmingSubmissionId the ID of the participation to find the submission for
     * @return an {@code Optional} containing the first {@code ProgrammingSubmission} with results, ordered by submission date in descending order,
     *         or an empty {@code Optional} if no submission is found
     */
    default Optional<ProgrammingSubmission> findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(long programmingSubmissionId) {
        Pageable pageable = PageRequest.of(0, 1); // fetch the first row
        // probably is not the prettiest variant, but we need a way to fetch the first row only, as sql limit does not work with JPQL, as the latter is SQL agnostic
        List<ProgrammingSubmissionIdAndSubmissionDateDTO> result = findFirstIdByParticipationIdOrderBySubmissionDateDesc(programmingSubmissionId, pageable);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        long id = result.getFirst().programmingSubmissionId();
        return findProgrammingSubmissionWithResultsById(id);
    }

    @Query("""
            SELECT new de.tum.cit.aet.artemis.service.dto.ProgrammingSubmissionIdAndSubmissionDateDTO(s.id, s.submissionDate)
            FROM ProgrammingSubmission s
                JOIN s.participation p
                JOIN p.exercise e
            WHERE p.id = :participationId
                AND (s.type = de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.INSTRUCTOR
                    OR s.type = de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.TEST
                    OR e.dueDate IS NULL
                    OR s.submissionDate <= e.dueDate)
            ORDER BY s.submissionDate DESC
            """)
    List<ProgrammingSubmissionIdAndSubmissionDateDTO> findSubmissionIdsAndDatesByParticipationId(@Param("participationId") long participationId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "results" })
    List<ProgrammingSubmission> findSubmissionsWithResultsByIdIn(List<Long> ids);

    /**
     * Provide a list of graded submissions. To be graded a submission must:
     * - be of type 'INSTRUCTOR' or 'TEST'
     * - have a submission date before the exercise due date
     * - or related to an exercise without a due date
     *
     * @param participationId to which the submissions belong.
     * @param pageable        Pageable
     * @return ProgrammingSubmission list (can be empty!)
     */
    default List<ProgrammingSubmission> findGradedByParticipationIdWithResultsOrderBySubmissionDateDesc(long participationId, Pageable pageable) {
        List<Long> ids = findSubmissionIdsAndDatesByParticipationId(participationId, pageable).stream().map(ProgrammingSubmissionIdAndSubmissionDateDTO::programmingSubmissionId)
                .toList();

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        return findSubmissionsWithResultsByIdIn(ids);
    }

    @EntityGraph(type = LOAD, attributePaths = "results.feedbacks")
    Optional<ProgrammingSubmission> findWithEagerResultsAndFeedbacksById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.testCase", "results.feedbacks.longFeedbackText", "buildLogEntries" })
    Optional<ProgrammingSubmission> findWithEagerResultsAndFeedbacksAndBuildLogsById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.testCase", "results.assessor" })
    Optional<ProgrammingSubmission> findWithEagerResultsFeedbacksTestCasesAssessorById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "buildLogEntries" })
    Optional<ProgrammingSubmission> findWithEagerBuildLogEntriesById(long submissionId);

    @Query("""
            SELECT s
            FROM ProgrammingSubmission s
                LEFT JOIN FETCH s.results r
            WHERE r.id = :resultId
            """)
    Optional<ProgrammingSubmission> findByResultId(@Param("resultId") long resultId);

    @Query("""
            SELECT DISTINCT s
            FROM ProgrammingSubmission s
                LEFT JOIN FETCH s.results r
            WHERE s.participation.id = :participationId
            """)
    List<ProgrammingSubmission> findAllByParticipationIdWithResults(@Param("participationId") long participationId);

    /**
     * Get the programming submission with the given id from the database. The submission is loaded together with exercise it belongs to, its result, the feedback of the result and
     * the assessor of the result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the programming submission with the given id
     */
    @NotNull
    default ProgrammingSubmission findByIdWithResultsFeedbacksAssessorTestCases(long submissionId) {
        return getValueElseThrow(findWithEagerResultsFeedbacksTestCasesAssessorById(submissionId), submissionId);
    }

    @NotNull
    default ProgrammingSubmission findByResultIdElseThrow(long resultId) {
        return getValueElseThrow(findByResultId(resultId));
    }
}
