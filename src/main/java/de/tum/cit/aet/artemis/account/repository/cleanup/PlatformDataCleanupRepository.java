package de.tum.cit.aet.artemis.account.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.admin.domain.DataExport;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Removes what the surrounding platform holds for a user that is being deleted permanently: their data exports, the
 * plagiarism cases they are party to, their LTI identities and their conversations with Iris.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 *
 * <p>
 * A plagiarism case is deleted with the student it concerns, but only detached from the person who ruled on it: the
 * verdict is part of the case, and the case belongs to the student rather than to the instructor who decided it.
 * Language model usage is likewise only detached, because what it records is cost against a course.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PlatformDataCleanupRepository extends ArtemisJpaRepository<DataExport, Long> {

    @Query("""
            SELECT export.user.id AS userId, COUNT(export) AS count
            FROM DataExport export
            WHERE export.user.id IN :userIds
            GROUP BY export.user.id
            """)
    List<UserReferenceCount> countDataExports(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM DataExport export
            WHERE export.user.id = :userId
            """)
    int deleteDataExports(@Param("userId") long userId);

    @Query("""
            SELECT trace.userId AS userId, COUNT(trace) AS count
            FROM LLMTokenUsageTrace trace
            WHERE trace.userId IN :userIds
            GROUP BY trace.userId
            """)
    List<UserReferenceCount> countLlmTokenUsageTraces(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE LLMTokenUsageTrace trace
            SET trace.userId = NULL
            WHERE trace.userId = :userId
            """)
    int detachLlmTokenUsageTraces(@Param("userId") long userId);

    @Query("""
            SELECT plagiarismCase.student.id AS userId, COUNT(plagiarismCase) AS count
            FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.student.id IN :userIds
            GROUP BY plagiarismCase.student.id
            """)
    List<UserReferenceCount> countPlagiarismCases(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.student.id = :userId
            """)
    int deletePlagiarismCases(@Param("userId") long userId);

    @Query("""
            SELECT plagiarismCase.verdictBy.id AS userId, COUNT(plagiarismCase) AS count
            FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.verdictBy.id IN :userIds
            GROUP BY plagiarismCase.verdictBy.id
            """)
    List<UserReferenceCount> countPlagiarismVerdicts(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE PlagiarismCase plagiarismCase
            SET plagiarismCase.verdictBy = NULL
            WHERE plagiarismCase.verdictBy.id = :userId
            """)
    int detachPlagiarismVerdicts(@Param("userId") long userId);

    @Query("""
            SELECT launch.user.id AS userId, COUNT(launch) AS count
            FROM LtiResourceLaunch launch
            WHERE launch.user.id IN :userIds
            GROUP BY launch.user.id
            """)
    List<UserReferenceCount> countLtiResourceLaunches(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LtiResourceLaunch launch
            WHERE launch.user.id = :userId
            """)
    int deleteLtiResourceLaunches(@Param("userId") long userId);

    @Query("""
            SELECT identity.userId AS userId, COUNT(identity) AS count
            FROM UserLti identity
            WHERE identity.userId IN :userIds
            GROUP BY identity.userId
            """)
    List<UserReferenceCount> countLtiIdentities(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserLti identity
            WHERE identity.userId = :userId
            """)
    int deleteLtiIdentities(@Param("userId") long userId);

    @Query("""
            SELECT session.userId AS userId, COUNT(session) AS count
            FROM IrisSession session
            WHERE session.userId IN :userIds
            GROUP BY session.userId
            """)
    List<UserReferenceCount> countIrisSessions(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM IrisSession session
            WHERE session.userId = :userId
            """)
    int deleteIrisSessions(@Param("userId") long userId);

    /**
     * The archives generated for the account's data exports. The rows go with the account, but the files live outside
     * the database and have to be scheduled for deletion separately.
     *
     * @param userId the account being deleted
     * @return the archive paths, without the exports whose archive was already cleaned up
     */
    @Query("""
            SELECT export.filePath
            FROM DataExport export
            WHERE export.user.id = :userId
                AND export.filePath IS NOT NULL
            """)
    List<String> findDataExportFilePaths(@Param("userId") long userId);

    /**
     * The plagiarism cases the account is the subject of.
     *
     * @param userId the account being deleted
     * @return the ids of those cases
     */
    @Query("""
            SELECT plagiarismCase.id
            FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.student.id = :userId
            """)
    List<Long> findPlagiarismCaseIdsOfStudent(@Param("userId") long userId);

    /**
     * The plagiarism cases a team is the subject of. A team that is removed with its only member takes these with it,
     * and the foreign key from a case to its team refuses the deletion while any is left.
     *
     * @param teamId the team being deleted
     * @return the ids of those cases
     */
    @Query("""
            SELECT plagiarismCase.id
            FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.team.id = :teamId
            """)
    List<Long> findPlagiarismCaseIdsOfTeam(@Param("teamId") long teamId);

    /**
     * Detaches the submissions from the given plagiarism cases, so that the cases can be removed. The submissions stay:
     * they are evidence in a comparison that also concerns the other student.
     *
     * @param plagiarismCaseIds the cases being removed
     * @return how many submissions were detached
     */
    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE PlagiarismSubmission submission
            SET submission.plagiarismCase = NULL
            WHERE submission.plagiarismCase.id IN :plagiarismCaseIds
            """)
    int detachPlagiarismSubmissions(@Param("plagiarismCaseIds") Collection<Long> plagiarismCaseIds);

    /**
     * Deletes the given plagiarism cases once nothing points at them any more.
     *
     * @param plagiarismCaseIds the cases being removed
     * @return how many cases were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.id IN :plagiarismCaseIds
            """)
    int deletePlagiarismCasesById(@Param("plagiarismCaseIds") Collection<Long> plagiarismCaseIds);
}
