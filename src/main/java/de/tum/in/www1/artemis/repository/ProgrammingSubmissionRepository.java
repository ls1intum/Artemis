package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

record ProgrammingSubmissionAndSubmissionDate(ProgrammingSubmission programmingSubmission, ZonedDateTime submissionDate) {
}

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

    Optional<ProgrammingSubmission> findFirstByParticipationIdOrderBySubmissionDateDesc(long participationId);

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
        var programmingSubmissionOptional = findFirstByParticipationIdOrderBySubmissionDateDesc(programmingSubmissionId);
        if (programmingSubmissionOptional.isEmpty()) {
            return Optional.empty();
        }
        var id = programmingSubmissionOptional.get().getId();
        return findProgrammingSubmissionWithResultsById(id);
    }

    @Query("""
            SELECT new de.tum.in.www1.artemis.repository.ProgrammingSubmissionAndSubmissionDate(s, s.submissionDate)
            FROM ProgrammingSubmission s
            JOIN s.participation p
            JOIN p.exercise e
            WHERE p.id = :participationId
              AND (s.type = de.tum.in.www1.artemis.domain.enumeration.SubmissionType.INSTRUCTOR
                   OR s.type = de.tum.in.www1.artemis.domain.enumeration.SubmissionType.TEST
                   OR e.dueDate IS NULL
                   OR s.submissionDate <= e.dueDate)
            ORDER BY s.submissionDate DESC
            """)
    List<ProgrammingSubmissionAndSubmissionDate> findSubmissionIdsAndDatesByParticipationId(@Param("participationId") long participationId, Pageable pageable);

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
        List<Long> ids = findSubmissionIdsAndDatesByParticipationId(participationId, pageable).stream().map(ProgrammingSubmissionAndSubmissionDate::programmingSubmission)
                .map(DomainObject::getId).toList();

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
        return findWithEagerResultsFeedbacksTestCasesAssessorById(submissionId).orElseThrow(() -> new EntityNotFoundException("Programming Submission", submissionId));
    }

    @NotNull
    default ProgrammingSubmission findByResultIdElseThrow(long resultId) {
        return findByResultId(resultId).orElseThrow(() -> new EntityNotFoundException("Programming Submission for Result", resultId));
    }
}
