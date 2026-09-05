package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.dto.BuildResultSubmissionDTO;
import de.tum.cit.aet.artemis.programming.dto.ParticipationCommitHashDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionCommitHashDTO;
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

    /**
     * Whether a submission for this participation and commit already exists.
     * <p>
     * Prefer this over loading the submission where only its existence matters: the loader above fetches the submission
     * together with its participation, team and the team's students, which is a lot of rows to answer a yes or no on a
     * path that runs on every push.
     *
     * @param participationId the id of the participation
     * @param commitHash      the commit hash of the submission
     * @return true if such a submission already exists
     */
    boolean existsByParticipationIdAndCommitHash(long participationId, String commitHash);

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
     * Returns what the grading code reads off the submission a build result belongs to, together with its newest result.
     * <p>
     * Deliberately a projection rather than the submission itself: a submission eagerly resolves its participation, that
     * its exercise, and that its course, so loading one ships the exercise's problem statement and the course's code of
     * conduct for every build. See {@link BuildResultSubmissionDTO}.
     *
     * @param submissionId the submission the build result belongs to
     * @return what grading reads off that submission, or empty if it no longer exists
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.BuildResultSubmissionDTO(
                submission.id, submission.buildFailed, submission.commitHash, submission.type, submission.submissionDate,
                submission.submitted, submission.exampleSubmission,
                latestResult.id, latestResult.assessmentType, latestResult.completionDate, latestResult.correctionRound)
            FROM ProgrammingSubmission submission
                LEFT JOIN submission.results latestResult
                    ON latestResult.id = (SELECT MAX(otherResult.id)
                                          FROM submission.results otherResult)
            WHERE submission.id = :submissionId
            """)
    Optional<BuildResultSubmissionDTO> findBuildResultSubmissionById(@Param("submissionId") long submissionId);

    /**
     * Records whether the build of a submission failed.
     * <p>
     * Deliberately a modifying query. This is one boolean on a row that already exists, and it used to be written by
     * saving the whole submission, which for a detached submission means a select of the submission together with its
     * participation, exercise and course, because those are eager associations. That select carried the exercise's
     * problem statement and the course's code of conduct for every build.
     *
     * @param submissionId the submission whose build outcome should be recorded
     * @param buildFailed  whether the build failed
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ProgrammingSubmission submission
            SET submission.buildFailed = :buildFailed
            WHERE submission.id = :submissionId
                AND submission.buildFailed <> :buildFailed
            """)
    void updateBuildFailed(@Param("submissionId") long submissionId, @Param("buildFailed") boolean buildFailed);

    /**
     * Returns what is needed to decide which submission of the participation a build result belongs to.
     * <p>
     * Deliberately a projection rather than the submissions themselves. A submission eagerly resolves its
     * participation, the participation its exercise, and the exercise its course, so loading every submission of a
     * participation in order to compare commit hashes made the database ship the problem statement, the grading
     * instructions and the course's code of conduct once per push the student ever made. See
     * {@link ProgrammingSubmissionCommitHashDTO}.
     *
     * @param participationId the participation whose submissions should be considered
     * @return the commit hash of every submission of the participation, with what is needed to order them
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionCommitHashDTO(s.id, s.type, s.commitHash, s.submissionDate)
            FROM ProgrammingSubmission s
            WHERE s.participation.id = :participationId
            """)
    List<ProgrammingSubmissionCommitHashDTO> findCommitHashesByParticipationId(@Param("participationId") long participationId);

    /**
     * Returns the distinct commit hashes of the manual submissions of the participation that already have a result.
     * <p>
     * This is what a submission policy counts. Counting it by loading every submission with its results was expensive
     * for the same reason as above, and the count needs nothing but the hashes. Selecting them distinct keeps the
     * previous behaviour exactly, including that submissions without a commit hash collapse into a single value.
     *
     * @param participationId the participation whose submissions should be counted
     * @return the distinct commit hashes of the participation's manual submissions that have a result
     */
    @Query("""
            SELECT DISTINCT s.commitHash
            FROM ProgrammingSubmission s
            WHERE s.participation.id = :participationId
                AND s.type = de.tum.cit.aet.artemis.exercise.domain.SubmissionType.MANUAL
                AND EXISTS (SELECT r.id
                            FROM s.results r)
            """)
    List<String> findDistinctManualCommitHashesWithResultByParticipationId(@Param("participationId") long participationId);

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
     * Returns the id of the latest submission (by submission date) for every student participation of the given
     * programming exercise. This allows callers to fetch only the latest submission per participation (e.g. via
     * {@link #findSubmissionsWithResultsByIdIn}) instead of loading the exercise's entire submission and result history,
     * which for large exercises meant transferring tens of thousands of rows.
     * <p>
     * Ordering is by {@code submissionDate} (not by id) to match the previous behavior: a submission created out of
     * order (e.g. a delayed fallback submission) can have the newest id but an older submission date, and must not be
     * treated as the latest. Ties on the same latest date are broken by the higher id.
     * <p>
     * Implemented as a {@code LEFT JOIN} anti-join (select the submission for which no strictly later submission of the
     * same participation exists) rather than a correlated subquery, following the project's "avoid subqueries" rule.
     * This returns exactly one id per participation.
     *
     * @param exerciseId the id of the programming exercise
     * @return the latest submission id per participation
     */
    @Query("""
            SELECT s.id
            FROM ProgrammingSubmission s
                LEFT JOIN ProgrammingSubmission s2
                    ON s2.participation.id = s.participation.id
                    AND (s2.submissionDate > s.submissionDate OR (s2.submissionDate = s.submissionDate AND s2.id > s.id))
            WHERE s.participation.exercise.id = :exerciseId
                AND s2.id IS NULL
            """)
    List<Long> findLatestSubmissionIdsByExerciseId(@Param("exerciseId") long exerciseId);

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
            return List.of();
        }

        return findSubmissionsWithResultsByIdIn(ids);
    }

    @EntityGraph(type = LOAD, attributePaths = "results.feedbacks")
    Optional<ProgrammingSubmission> findWithEagerResultsAndFeedbacksById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.longFeedbackText", "buildLogEntries", "participation.exercise" })
    Optional<ProgrammingSubmission> findWithEagerResultsAndFeedbacksAndBuildLogsById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.assessor" })
    Optional<ProgrammingSubmission> findWithEagerResultsFeedbacksAssessorById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "buildLogEntries" })
    Optional<ProgrammingSubmission> findWithEagerBuildLogEntriesById(long submissionId);

    @Query("""
            SELECT s
            FROM ProgrammingSubmission s
                LEFT JOIN FETCH s.results r
            WHERE r.id = :resultId
            """)
    Optional<ProgrammingSubmission> findByResultId(@Param("resultId") long resultId);

    /**
     * Get the programming submission with the given id from the database. The submission is loaded together with exercise it belongs to, its result, the feedback of the result and
     * the assessor of the result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the programming submission with the given id
     */
    @NonNull
    default ProgrammingSubmission findByIdWithResultsFeedbacksAssessor(long submissionId) {
        return getValueElseThrow(findWithEagerResultsFeedbacksAssessorById(submissionId), submissionId);
    }

    @NonNull
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

    @Query("""
            SELECT s
            FROM ProgrammingSubmission s
            WHERE s.participation.id = :participationId
                AND s.commitHash IS NOT NULL
            ORDER BY s.submissionDate ASC, s.id ASC
            """)
    List<ProgrammingSubmission> findByParticipationIdOrderBySubmissionDateAsc(@Param("participationId") long participationId);
}
