package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.dto.ParticipationCommitHashDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionIdAndSubmissionDateDTO;

/**
 * Spring Data JPA repository for the ProgrammingSubmission entity.
 */
@Profile(PROFILE_CORE)
@Lazy
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
            SELECT new de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionIdAndSubmissionDateDTO(ps.id, ps.submissionDate)
            FROM ProgrammingSubmission ps
            WHERE ps.participation.id = :participationId
            ORDER BY ps.submissionDate DESC
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
        long submissionId = result.getFirst().programmingSubmissionId();
        return findProgrammingSubmissionWithResultsById(submissionId);
    }

    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionIdAndSubmissionDateDTO(s.id, s.submissionDate)
            FROM ProgrammingSubmission s
                JOIN s.participation p
                JOIN p.exercise e
            WHERE p.id = :participationId
                AND (s.type = de.tum.cit.aet.artemis.exercise.domain.SubmissionType.INSTRUCTOR
                    OR s.type = de.tum.cit.aet.artemis.exercise.domain.SubmissionType.TEST
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
        var ids = findSubmissionIdsAndDatesByParticipationId(participationId, pageable).stream().map(ProgrammingSubmissionIdAndSubmissionDateDTO::programmingSubmissionId).toList();

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

    /**
     * <b>⚠️ ATTENTION: This query is expensive and should be only used in a scheduled job to avoid performance issues in the application.</b>
     * <br>
     * Find programming submissions where the latest submission per participation is older than the given start time but not older than the given end time
     * and does NOT have any results. Used for retriggering builds for submissions that did not get a result due to some hiccup in the CI system.
     * <br>
     * This query ensures that either the latest submission for a participation is returned (if it meets all criteria),
     * or no submission is returned for that participation at all. It does NOT return older submissions even if they are in the time range.
     *
     * @param startTime the earliest time to consider (oldest submissions)
     * @param endTime   the latest time to consider (newest submissions)
     * @param pageable  pagination information for slice-based retrieval
     * @return a slice of absolute latest programming submissions per participation without results in the given time range
     */
    @Query("""
            SELECT s
            FROM ProgrammingSubmission s
            WHERE s.submissionDate >= :startTime
                AND s.submissionDate <= :endTime
                AND s.results IS EMPTY
                AND s.submissionDate = (
                    SELECT MAX(s2.submissionDate)
                    FROM ProgrammingSubmission s2
                    WHERE s2.participation.id = s.participation.id
                )
            """)
    Slice<ProgrammingSubmission> findLatestProgrammingSubmissionsWithoutResultsInTimeRange(@Param("startTime") ZonedDateTime startTime, @Param("endTime") ZonedDateTime endTime,
            Pageable pageable);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.ParticipationCommitHashDTO(s.participation.id, s.commitHash)
            FROM ProgrammingSubmission s
            WHERE s.participation.id IN :loadedParticipationIds
                AND s.submissionDate = (
                    SELECT MAX(s2.submissionDate)
                    FROM ProgrammingSubmission s2
                    WHERE s2.participation.id = s.participation.id
                        AND (COALESCE(:filterLateSubmissionsIndividualDueDate, s2.participation.individualDueDate, :exerciseDueDate) IS NULL
                             OR s2.submissionDate <= COALESCE(:filterLateSubmissionsIndividualDueDate, s2.participation.individualDueDate, :exerciseDueDate))
                )
            """)
    Set<ParticipationCommitHashDTO> findLatestValidCommitHashForParticipations(@Param("loadedParticipationIds") Set<Long> loadedParticipationIds,
            @Param("filterLateSubmissionsIndividualDueDate") ZonedDateTime filterLateSubmissionsIndividualDueDate, @Param("exerciseDueDate") ZonedDateTime exerciseDueDate);
}
