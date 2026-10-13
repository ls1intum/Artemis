package de.tum.cit.aet.artemis.metis.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
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
     * Claims a pending check for generation on this node. Of several nodes or tasks that try to claim the same check, exactly one changes the row. The claim
     * is stored with the check, and every write of the run requires it: a run that lost the check meanwhile, for instance to a restart of the scheduling node
     * that put it back into the queue, can then no longer change it.
     *
     * @param checkId the check to claim
     * @param claim   a random number that identifies this claim
     * @return 1 if this caller claimed the check, 0 if it was not pending (any more)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING,
                c.generationClaim = :claim
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING
            """)
    int claimForGeneration(@Param("checkId") long checkId, @Param("claim") long claim);

    /**
     * Puts a claimed check back into the queue, for example after a transient failure of the LLM or when the effective due date moved into the future.
     *
     * @param checkId the claimed check
     * @param claim   the claim of the run that releases it
     * @return 1 if the check was released, 0 if it was not being generated under this claim (any more)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING,
                c.generationClaim = NULL
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING
                AND c.generationClaim = :claim
            """)
    int releaseGeneration(@Param("checkId") long checkId, @Param("claim") long claim);

    /**
     * Puts every check that is being generated back into the queue. Only the scheduling node generates, so when it starts, every such check is an orphan of a
     * crash or a shutdown. Their claims are cleared, so a run of a node that is still stopping cannot write any of them, also once one is claimed again.
     *
     * @return the number of released checks
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING,
                c.generationClaim = NULL
            WHERE c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING
            """)
    int releaseAllGenerating();

    /**
     * Stores the outcome of a successful generation: the next status, the snapshot of the run, its time and the effective due date it read, and clears an
     * earlier failure reason.
     *
     * @param checkId           the check that is being generated
     * @param nextStatus        {@link MetisCheckStatus#READY}, or {@link MetisCheckStatus#IN_REVIEW} when the exercise requires a review
     * @param commitHash        the commit the questions were generated from
     * @param llmModel          the model the LLM endpoint reported
     * @param promptVersion     the version of the prompt templates
     * @param generatedAt       when the run started that generated the questions
     * @param generationDueDate the effective due date the run read and took the commit up to
     * @param claim             the claim of the run
     * @return 1 if the check was updated, 0 if it was not being generated under this claim (any more), for example because it was reset meanwhile
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
                c.generationDueDate = :generationDueDate,
                c.failureReason = NULL,
                c.generationClaim = NULL
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING
                AND c.generationClaim = :claim
            """)
    int markGenerated(@Param("checkId") long checkId, @Param("nextStatus") MetisCheckStatus nextStatus, @Param("commitHash") String commitHash, @Param("llmModel") String llmModel,
            @Param("promptVersion") String promptVersion, @Param("generatedAt") ZonedDateTime generatedAt, @Param("generationDueDate") ZonedDateTime generationDueDate,
            @Param("claim") long claim);

    /**
     * Ends a generation without questions: the check is skipped or failed for a reason. The time of the run and the effective due date it read are stored as
     * for a check with questions, so that a later change of the due date is recognized the same way; the model and the prompt version of an earlier run are
     * cleared, since no questions of this run carry them.
     *
     * @param checkId           the check that is being generated
     * @param terminalStatus    {@link MetisCheckStatus#SKIPPED} or {@link MetisCheckStatus#FAILED}
     * @param failureReason     the name of the {@code MetisFailureReason}
     * @param commitHash        the commit the generation had reached, or null when it ended before a commit was known
     * @param generatedAt       when the run started
     * @param generationDueDate the effective due date the run read, or null when it ended before it read one
     * @param claim             the claim of the run
     * @return 1 if the check was updated, 0 if it was not being generated under this claim (any more)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = :terminalStatus,
                c.failureReason = :failureReason,
                c.commitHash = :commitHash,
                c.llmModel = NULL,
                c.promptVersion = NULL,
                c.generatedAt = :generatedAt,
                c.generationDueDate = :generationDueDate,
                c.generationClaim = NULL
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.GENERATING
                AND c.generationClaim = :claim
            """)
    int markUnavailable(@Param("checkId") long checkId, @Param("terminalStatus") MetisCheckStatus terminalStatus, @Param("failureReason") String failureReason,
            @Param("commitHash") @Nullable String commitHash, @Param("generatedAt") ZonedDateTime generatedAt,
            @Param("generationDueDate") @Nullable ZonedDateTime generationDueDate, @Param("claim") long claim);

    /**
     * Starts a check for its student. A check in review may be started too, once the student's window has opened, because unreviewed questions are released at
     * the window start; the service checks the window. The check starts only while the effective due date of its participation is the one its generation run
     * read: the service compares them before, and a change of the due date in between would otherwise start questions about code that no longer counts,
     * which no later reset could replace, since a started check is never reset. The guard reads the participation of the check and its exercise only: on
     * MySQL an update keeps shared locks on every row its subqueries read, so a subquery over all participations would hold the participations and exercises
     * of every student until each start commits.
     *
     * @param checkId   the check to start
     * @param startedAt when the student started
     * @return 1 if the check was started, 0 if it was neither ready nor in review, for example because a parallel request started it first, or if its effective
     *         due date changed since its generation run read it
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_PROGRESS,
                c.startedAt = :startedAt
            WHERE c.id = :checkId
                AND c.status IN (de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY, de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW)
                AND EXISTS (
                    SELECT p.id
                    FROM StudentParticipation p
                        JOIN p.exercise e
                    WHERE p.id = c.participationId
                        AND COALESCE(p.individualDueDate, e.dueDate) = c.generationDueDate
                )
            """)
    int markStarted(@Param("checkId") long checkId, @Param("startedAt") ZonedDateTime startedAt);

    /**
     * Reads the configuration of an exercise and holds a shared lock on its row until the surrounding transaction ends. The starts of the checks of the
     * exercise share it with each other, while a change of the settings waits for them ({@code MetisExerciseConfigRepository.updateSettingsAfterTheStarts}).
     *
     * @param exerciseId the exercise
     * @return the configuration, or empty if the exercise has none
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT cfg
            FROM MetisExerciseConfig cfg
            WHERE cfg.exerciseId = :exerciseId
            """)
    Optional<MetisExerciseConfig> findConfigForShare(@Param("exerciseId") long exerciseId);

    /**
     * Starts a check like {@link #markStarted}, while it holds a shared lock on the configuration of its exercise: the rating scale, which every score is read
     * against, cannot change between the guard of a settings update and this start. The check is started only while the configuration it reads under the
     * lock is still enabled, since the service read it before the lock and an editor may have switched the check off in between.
     *
     * @param checkId    the check
     * @param exerciseId the exercise of the check
     * @param startedAt  when the student started it
     * @return 1 if the check was started now, 0 if it was not ready or in review (any more), if its effective due date changed since its generation run, or if
     *         the check of the exercise is switched off
     */
    @Transactional // ok because the start has to hold the lock of the configuration that a change of the rating scale waits for, and read the switch under it
    default int markStartedSharingTheConfig(long checkId, long exerciseId, ZonedDateTime startedAt) {
        if (!findConfigForShare(exerciseId).map(MetisExerciseConfig::isEnabled).orElse(false)) {
            return 0;
        }
        return markStarted(checkId, startedAt);
    }

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
     * Reads a check that is in progress and locks its row until the surrounding transaction ends. Every transition that moves a started check on after an
     * answer, by a follow-up question, the next main question or the completion, takes this lock first and decides on the questions as they are then, so that
     * the request that stored the latest answer and the repair of an interrupted transition, which a read runs, never both move the check on. The first main
     * question, which the start shows, does not need it: the only other writer that can show a question then is a repair, which can show nothing but that
     * same question, and showing a question is guarded by its not having been shown.
     *
     * @param checkId the check
     * @return the check, or empty if it is not in progress
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c
            FROM MetisCheck c
            WHERE c.id = :checkId
                AND c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_PROGRESS
            """)
    Optional<MetisCheck> findInProgressForUpdate(@Param("checkId") long checkId);

    /**
     * Whether a question of a check is open: shown and not answered.
     *
     * @param checkId the check
     * @return true if a question is open
     */
    @Query("""
            SELECT COUNT(q) > 0
            FROM MetisQuestion q
            WHERE q.checkId = :checkId
                AND q.shownAt IS NOT NULL
                AND q.answeredAt IS NULL
            """)
    boolean hasOpenQuestion(@Param("checkId") long checkId);

    /**
     * How many questions of a check have been shown; see {@code MetisQuestionRepository.countShownQuestionsOfCheck}.
     *
     * @param checkId the check
     * @return the number of shown questions
     */
    @Query("""
            SELECT COUNT(q)
            FROM MetisQuestion q
            WHERE q.checkId = :checkId
                AND q.shownAt IS NOT NULL
            """)
    long countShownQuestions(@Param("checkId") long checkId);

    /**
     * Completes a check under the lock of the check, while it is in progress, no question of it is open and no question was shown since the caller read the
     * questions. So it never completes a check whose follow-up question the request that stored the latest answer has just asked, nor one that another
     * transition moved on meanwhile, also when the student has answered the question it opened by then.
     *
     * @param checkId     the check
     * @param completedAt when it is completed
     * @param shownBefore how many questions of the check were shown when the caller read them
     * @return whether it was completed now
     */
    @Transactional // ok because the decision has to read the questions under the lock of the check, which this transaction holds until the write is committed
    default boolean completeIfNoQuestionIsOpen(long checkId, ZonedDateTime completedAt, long shownBefore) {
        return findInProgressForUpdate(checkId).isPresent() && !hasOpenQuestion(checkId) && countShownQuestions(checkId) == shownBefore && markCompleted(checkId, completedAt) == 1;
    }

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
     * Puts the checks of an exercise back into the queue whose effective due date an instructor changed after the check was generated, skipped or failed:
     * those whose effective due date lies in the future again, and those whose effective due date differs from the one their last generation run read. The
     * run took the student's code from the last submission up to the due date it read, so the second case catches every change made after that: an extension
     * or a shortening, also one to a time that has already passed, and also one that passed while the model was working, the scheduling node was down or the
     * check was switched off meanwhile. Claims only take checks whose effective due date has passed, so the questions are generated again from the state at
     * the new due date. Started checks are never touched, and a check skipped when it was created, or failed before its run read the due date, only goes back
     * while its due date lies in the future. The rule compares the due dates of each check with the participation of that check only, so the database reads
     * the participations of the exercise once and not once for every check.
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
                    WHERE p.exercise.id = :exerciseId
                )
                AND EXISTS (
                    SELECT own.id
                    FROM StudentParticipation own
                        JOIN own.exercise e
                    WHERE own.id = c.participationId
                        AND (COALESCE(own.individualDueDate, e.dueDate) > :now
                            OR COALESCE(own.individualDueDate, e.dueDate) <> c.generationDueDate)
                )
            """)
    int resetChecksWithChangedDueDate(@Param("exerciseId") long exerciseId, @Param("now") ZonedDateTime now);

    /**
     * Puts one check back into the queue under the rule of {@link #resetChecksWithChangedDueDate}: it was not started, and its effective due date lies in the
     * future or differs from the one its last generation run read. The sweep looks only at exercises within its horizon, so the read of a student whose
     * exercise lies outside it puts the student's check back this way; claims take it like any other. Like the start, it reads the participation of the check
     * and its exercise only, so on MySQL it locks no row of another student.
     *
     * @param checkId the check
     * @param now     the current time
     * @return 1 if the check went back into the queue, 0 otherwise
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisCheck c
            SET c.status = de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.PENDING,
                c.failureReason = NULL
            WHERE c.id = :checkId
                AND c.status IN (de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.READY, de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.IN_REVIEW,
                    de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.SKIPPED, de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus.FAILED)
                AND c.startedAt IS NULL
                AND EXISTS (
                    SELECT p.id
                    FROM StudentParticipation p
                        JOIN p.exercise e
                    WHERE p.id = c.participationId
                        AND (COALESCE(p.individualDueDate, e.dueDate) > :now
                            OR COALESCE(p.individualDueDate, e.dueDate) <> c.generationDueDate)
                )
            """)
    int resetCheckWithChangedDueDate(@Param("checkId") long checkId, @Param("now") ZonedDateTime now);
}
