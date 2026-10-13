package de.tum.cit.aet.artemis.metis.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.dto.MetisCheckCandidateDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisCheckOwnerDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerationTargetDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisOwnParticipationDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisParticipationRowDTO;

/**
 * Spring Data JPA repository for the {@link MetisCheck} entity, one row per student participation that has a Metis comprehension check.
 * <p>
 * Every change of a check's status is a guarded {@code UPDATE}: the state the transition starts from is part of the {@code WHERE} clause, and the caller acts on
 * the number of rows the statement changed. A check that another node, a parallel request or a reset moved on in the meantime is therefore left alone instead of
 * being overwritten, without a transaction spanning the read and the write. The statements bypass the persistence context, so callers read a check again after
 * changing it.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Repository
public interface MetisCheckRepository extends ArtemisJpaRepository<MetisCheck, Long> {

    /**
     * The check of one participation. At most one exists, which the unique key on the participation guarantees.
     *
     * @param participationId the participation the check belongs to
     * @return the check, if one has been created for that participation
     */
    Optional<MetisCheck> findByParticipationId(long participationId);

    /**
     * The participations of an exercise that should get a check now and do not have one yet: graded (not practice) participations of a single student (not a
     * team) that have a repository and whose effective due date, the individual due date if there is one and else the due date of the exercise, has passed the
     * cutoff. The student must hold the student role in the exercise's course and no other role there: course roles add up, so a tutor can keep an old student
     * role, and staff get no check.
     * <p>
     * The rows come ordered by participation id, so that concurrent inserters take the locks of the unique key in the same order.
     *
     * @param exerciseId the exercise whose participations are considered
     * @param cutoff     the latest effective due date a participation may have
     * @return the participations without a check, each with its student
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisCheckCandidateDTO(p.id, student.id)
            FROM ProgrammingExerciseStudentParticipation p
                JOIN p.student student
                JOIN p.exercise e
                JOIN UserCourseRole ucr ON ucr.user.id = student.id AND ucr.course.id = e.course.id
                    AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
                LEFT JOIN MetisCheck c ON c.participationId = p.id
            WHERE e.id = :exerciseId
                AND c.id IS NULL
                AND COALESCE(p.testRun, FALSE) = FALSE
                AND p.repositoryUri IS NOT NULL
                AND COALESCE(p.individualDueDate, e.dueDate) <= :cutoff
                AND NOT EXISTS (
                    SELECT r2.role
                    FROM UserCourseRole r2
                    WHERE r2.user.id = student.id
                        AND r2.course.id = e.course.id
                        AND r2.role <> de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
                )
            ORDER BY p.id ASC
            """)
    List<MetisCheckCandidateDTO> findCheckCandidates(@Param("exerciseId") long exerciseId, @Param("cutoff") ZonedDateTime cutoff);

    /**
     * Everything the generation of a check needs to know about it, in one statement: the check's state and commit, the participation with its repository and
     * effective due date, the student, the exercise and the course.
     *
     * @param checkId the check to read
     * @return the target, or empty when the check does not exist (any more) or belongs to an exercise without a course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisGenerationTargetDTO(c.id, c.status, c.commitHash, p.id, student.id, p.repositoryUri,
                COALESCE(p.individualDueDate, e.dueDate), e.id, course.id)
            FROM MetisCheck c
                JOIN ProgrammingExerciseStudentParticipation p ON p.id = c.participationId
                JOIN p.student student
                JOIN p.exercise e
                JOIN e.course course
            WHERE c.id = :checkId
            """)
    Optional<MetisGenerationTargetDTO> findGenerationTarget(@Param("checkId") long checkId);

    /**
     * The commits of a participation's submissions up to a deadline, latest first. The submission date is the server's time of the push, never the committer
     * time, which the student's machine sets; submissions with the same date are ordered by their id, so the later one wins.
     *
     * @param participationId the participation whose submissions are read
     * @param deadline        the latest submission date that counts
     * @param pageable        how many commits to return; the generation asks for the first one only
     * @return the commit hashes, latest first
     */
    @Query("""
            SELECT s.commitHash
            FROM ProgrammingSubmission s
            WHERE s.participation.id = :participationId
                AND s.commitHash IS NOT NULL
                AND s.submissionDate <= :deadline
            ORDER BY s.submissionDate DESC, s.id DESC
            """)
    List<String> findLatestCommitHashesUpTo(@Param("participationId") long participationId, @Param("deadline") ZonedDateTime deadline, Pageable pageable);

    /**
     * The graded (not practice) participations of one user in an exercise, each with its effective due date and the id of its check, if one exists. Students
     * reach their check through the exercise alone, so this is how their participation is found.
     *
     * @param exerciseId the exercise
     * @param login      the login of the requesting user
     * @return the user's graded participations, usually one
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisOwnParticipationDTO(p.id, student.id, p.repositoryUri, COALESCE(p.individualDueDate, e.dueDate), c.id)
            FROM ProgrammingExerciseStudentParticipation p
                JOIN p.student student
                JOIN p.exercise e
                LEFT JOIN MetisCheck c ON c.participationId = p.id
            WHERE e.id = :exerciseId
                AND student.login = :login
                AND COALESCE(p.testRun, FALSE) = FALSE
            """)
    List<MetisOwnParticipationDTO> findOwnGradedParticipations(@Param("exerciseId") long exerciseId, @Param("login") String login);

    /**
     * One row per check of an exercise with the student's login and name and every column of the check, so that the dashboard reads all checks in a single
     * statement.
     *
     * @param exerciseId the exercise
     * @return the rows, ordered by the student's login
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisParticipationRowDTO(p.id, student.id, student.login, student.firstName, student.lastName,
                c.id, c.status, c.commitHash, c.llmModel, c.promptVersion, c.failureReason, c.generatedAt, c.startedAt, c.completedAt)
            FROM MetisCheck c
                JOIN StudentParticipation p ON p.id = c.participationId
                JOIN p.student student
            WHERE p.exercise.id = :exerciseId
            ORDER BY student.login ASC
            """)
    List<MetisParticipationRowDTO> findDashboardRows(@Param("exerciseId") long exerciseId);

    /**
     * The checks that wait for generation: pending checks of exercises whose configuration is enabled and whose effective due date has passed the cutoff.
     * <p>
     * The order approximates each student's window start: the earliest window of a tutorial group the student is registered in, else the start of the
     * configuration's window, else the due date of the exercise. The group whose window opens first is therefore generated first. The registration join covers
     * every group of the student; only the windows of the check's exercise match.
     *
     * @param cutoff   the latest effective due date a check's participation may have
     * @param pageable how many ids to return; the dispatcher asks for a few and claims the first one it wins
     * @return the ids of the claimable checks, the earliest window first
     */
    @Query("""
            SELECT c.id
            FROM MetisCheck c
                JOIN StudentParticipation p ON p.id = c.participationId
                JOIN p.exercise e
                JOIN MetisExerciseConfig cfg ON cfg.exerciseId = e.id
                LEFT JOIN TutorialGroupRegistration r ON r.student.id = p.student.id
                LEFT JOIN MetisGroupWindow w ON w.exerciseId = e.id AND w.tutorialGroupId = r.tutorialGroup.id
            WHERE c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING
                AND cfg.enabled = TRUE
                AND COALESCE(p.individualDueDate, e.dueDate) <= :cutoff
            GROUP BY c.id, cfg.windowStart, e.dueDate
            ORDER BY COALESCE(MIN(w.windowStart), cfg.windowStart, e.dueDate) ASC, c.id ASC
            """)
    List<Long> findClaimableCheckIds(@Param("cutoff") ZonedDateTime cutoff, Pageable pageable);

    /**
     * The checks of some exercises that were skipped for one reason, each with its student, so that the students' current decisions can be looked up in one
     * query. Only participations of a single student are considered; checks never exist for teams.
     *
     * @param exerciseIds   the exercises whose checks are read
     * @param failureReason the stored reason, the name of a {@code MetisFailureReason}
     * @return the skipped checks with their students
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisCheckOwnerDTO(c.id, student.id)
            FROM MetisCheck c
                JOIN StudentParticipation p ON p.id = c.participationId
                JOIN p.student student
            WHERE p.exercise.id IN :exerciseIds
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.SKIPPED
                AND c.failureReason = :failureReason
            """)
    List<MetisCheckOwnerDTO> findSkippedChecksWithReason(@Param("exerciseIds") Collection<Long> exerciseIds, @Param("failureReason") String failureReason);

    /**
     * Claims a pending check for generation on this node. Of several nodes or tasks that try to claim the same check, exactly one changes the row.
     *
     * @param checkId the check to claim
     * @return 1 if this caller claimed the check, 0 if it was not pending (any more)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING
            """)
    int claimForGeneration(@Param("checkId") long checkId);

    /**
     * Puts a claimed check back into the queue, for example after a transient failure of the LLM or when the effective due date moved into the future.
     *
     * @param checkId the claimed check
     * @return 1 if the check was released, 0 if it was not being generated (any more)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING
            """)
    int releaseGeneration(@Param("checkId") long checkId);

    /**
     * Puts every check that is being generated back into the queue. Only the scheduling node generates, so when it starts, every such check is an orphan of a
     * crash or a shutdown.
     *
     * @return the number of released checks
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING
            WHERE c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING
            """)
    int releaseAllGenerating();

    /**
     * Stores the outcome of a successful generation: the next status, the snapshot of the run and the time, and clears an earlier failure reason.
     *
     * @param checkId       the check that is being generated
     * @param nextStatus    {@link MetisCheckStatus#READY}, or {@link MetisCheckStatus#IN_REVIEW} when the exercise requires a review
     * @param commitHash    the commit the questions were generated from
     * @param llmModel      the model the LLM endpoint reported
     * @param promptVersion the version of the prompt templates
     * @param generatedAt   when the questions were stored
     * @return 1 if the check was updated, 0 if it was not being generated (any more), for example because it was reset meanwhile
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = :nextStatus,
                c.commitHash = :commitHash,
                c.llmModel = :llmModel,
                c.promptVersion = :promptVersion,
                c.generatedAt = :generatedAt,
                c.failureReason = NULL
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING
            """)
    int markGenerated(@Param("checkId") long checkId, @Param("nextStatus") MetisCheckStatus nextStatus, @Param("commitHash") String commitHash, @Param("llmModel") String llmModel,
            @Param("promptVersion") String promptVersion, @Param("generatedAt") ZonedDateTime generatedAt);

    /**
     * Ends a generation without questions: the check is skipped or failed for a reason.
     *
     * @param checkId        the check that is being generated
     * @param terminalStatus {@link MetisCheckStatus#SKIPPED} or {@link MetisCheckStatus#FAILED}
     * @param failureReason  the name of the {@code MetisFailureReason}
     * @param commitHash     the commit the generation had reached, or null when it ended before a commit was known
     * @return 1 if the check was updated, 0 if it was not being generated (any more)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = :terminalStatus,
                c.failureReason = :failureReason,
                c.commitHash = :commitHash
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING
            """)
    int markUnavailable(@Param("checkId") long checkId, @Param("terminalStatus") MetisCheckStatus terminalStatus, @Param("failureReason") String failureReason,
            @Param("commitHash") @Nullable String commitHash);

    /**
     * Starts a check for its student. A check in review may be started too, once the student's window has opened, because unreviewed questions are released at
     * the window start; the service checks the window.
     *
     * @param checkId   the check to start
     * @param startedAt when the student started
     * @return 1 if the check was started, 0 if it was neither ready nor in review, for example because a parallel request started it first
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_PROGRESS,
                c.startedAt = :startedAt
            WHERE c.id = :checkId
                AND c.status IN (de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY, de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW)
            """)
    int markStarted(@Param("checkId") long checkId, @Param("startedAt") ZonedDateTime startedAt);

    /**
     * Completes a started check.
     *
     * @param checkId     the check to complete
     * @param completedAt when the last answer left nothing more to ask
     * @return 1 if the check was completed, 0 if it was not in progress (any more)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.COMPLETED,
                c.completedAt = :completedAt
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_PROGRESS
            """)
    int markCompleted(@Param("checkId") long checkId, @Param("completedAt") ZonedDateTime completedAt);

    /**
     * Releases a check in review to its student once every main question of it is approved. Follow-up questions are never reviewed and do not count.
     *
     * @param checkId the check in review
     * @return 1 if the check was released, 0 if it was not in review or a main question is still unapproved
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW
                AND NOT EXISTS (
                    SELECT q.id
                    FROM MetisQuestion q
                    WHERE q.checkId = c.id
                        AND q.parentQuestionId IS NULL
                        AND q.approvedAt IS NULL
                )
            """)
    int releaseIfAllMainQuestionsApproved(@Param("checkId") long checkId);

    /**
     * Releases every check of an exercise that is in review and whose main questions are all approved, after an editor approved all questions at once.
     *
     * @param exerciseId the exercise
     * @return the number of released checks
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY
            WHERE c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW
                AND c.participationId IN (
                    SELECT p.id
                    FROM StudentParticipation p
                    WHERE p.exercise.id = :exerciseId
                )
                AND NOT EXISTS (
                    SELECT q.id
                    FROM MetisQuestion q
                    WHERE q.checkId = c.id
                        AND q.parentQuestionId IS NULL
                        AND q.approvedAt IS NULL
                )
            """)
    int releaseApprovedChecksOfExercise(@Param("exerciseId") long exerciseId);

    /**
     * Releases every check of an exercise that is in review, approved or not, because the review is no longer required.
     *
     * @param exerciseId the exercise
     * @return the number of released checks
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY
            WHERE c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW
                AND c.participationId IN (
                    SELECT p.id
                    FROM StudentParticipation p
                    WHERE p.exercise.id = :exerciseId
                )
            """)
    int releaseAllInReviewOfExercise(@Param("exerciseId") long exerciseId);

    /**
     * Returns the ready checks of an exercise to review when a review becomes required, so that checks generated before the switch are gated as well. Only
     * checks that nobody has started and that still have an unapproved main question move.
     *
     * @param exerciseId the exercise
     * @return the number of checks that went back to review
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW
            WHERE c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY
                AND c.startedAt IS NULL
                AND c.participationId IN (
                    SELECT p.id
                    FROM StudentParticipation p
                    WHERE p.exercise.id = :exerciseId
                )
                AND EXISTS (
                    SELECT q.id
                    FROM MetisQuestion q
                    WHERE q.checkId = c.id
                        AND q.parentQuestionId IS NULL
                        AND q.approvedAt IS NULL
                )
            """)
    int returnUnstartedToReviewOfExercise(@Param("exerciseId") long exerciseId);

    /**
     * Returns one ready check to review while one of its questions is regenerated, so that its student does not start it with a question nobody has seen.
     *
     * @param checkId the check
     * @return 1 if the check went back to review, 0 if it was not ready or has been started
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY
                AND c.startedAt IS NULL
            """)
    int returnToReview(@Param("checkId") long checkId);

    /**
     * Puts the failed and skipped checks of an exercise that ended for one of the given reasons back into the queue and clears their reason. The reasons of
     * failed and skipped checks are disjoint, so the reasons alone pick the checks of both states.
     *
     * @param exerciseId     the exercise
     * @param failureReasons the names of the {@code MetisFailureReason}s to requeue
     * @return the number of requeued checks
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING,
                c.failureReason = NULL
            WHERE c.status IN (de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.FAILED, de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.SKIPPED)
                AND c.failureReason IN :failureReasons
                AND c.participationId IN (
                    SELECT p.id
                    FROM StudentParticipation p
                    WHERE p.exercise.id = :exerciseId
                )
            """)
    int requeueChecksOfExercise(@Param("exerciseId") long exerciseId, @Param("failureReasons") Collection<String> failureReasons);

    /**
     * Puts some skipped checks back into the queue, for example those whose students have given their consent since, and clears their reason. A check that
     * meanwhile left the skipped state or was skipped for another reason is left alone.
     *
     * @param checkIds      the checks to requeue
     * @param failureReason the reason the checks must still have been skipped for
     * @return the number of requeued checks
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING,
                c.failureReason = NULL
            WHERE c.id IN :checkIds
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.SKIPPED
                AND c.failureReason = :failureReason
            """)
    int requeueSkippedChecks(@Param("checkIds") Collection<Long> checkIds, @Param("failureReason") String failureReason);

    /**
     * Puts the checks of an exercise back into the queue whose effective due date lies in the future again, because an instructor extended it after the check
     * was generated or skipped. Claims only take checks whose effective due date has passed, so the questions are generated again from the state after the new
     * due date. Started checks are never touched.
     *
     * @param exerciseId the exercise
     * @param now        the current time
     * @return the number of checks that went back into the queue
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING,
                c.failureReason = NULL
            WHERE c.status IN (de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY, de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW,
                    de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.SKIPPED, de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.FAILED)
                AND c.startedAt IS NULL
                AND c.participationId IN (
                    SELECT p.id
                    FROM StudentParticipation p
                        JOIN p.exercise e
                    WHERE e.id = :exerciseId
                        AND COALESCE(p.individualDueDate, e.dueDate) > :now
                )
            """)
    int resetChecksWithFutureDueDate(@Param("exerciseId") long exerciseId, @Param("now") ZonedDateTime now);
}
