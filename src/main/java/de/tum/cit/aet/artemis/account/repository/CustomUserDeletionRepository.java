package de.tum.cit.aet.artemis.account.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Repository fragment for the low-level operations required by permanent user deletion.
 *
 * Dynamic table and column names cannot be expressed through derived Spring Data queries. Keeping them in this
 * repository fragment preserves the repository boundary: deletion services orchestrate domain behavior without
 * accessing {@code EntityManager} or {@code JdbcTemplate} directly.
 */
public interface CustomUserDeletionRepository {

    record UserReference(String policyName, String tableName, String columnName) {
    }

    record ExamUserImagePaths(@Nullable String signingImagePath, @Nullable String studentImagePath) {
    }

    Optional<User> findByIdForDeletion(long userId);

    /**
     * Takes the account out of use: deactivates it, so that no authentication provider accepts it any more, and drops
     * its course memberships.
     *
     *
     * @param userId the account being deleted
     */
    void closeAccount(long userId);

    void clearLearnerProfile(long userId);

    void deleteLearnerProfile(long learnerProfileId);

    int deleteUserRow(long userId);

    boolean isNotEnrolledUserStillDueForDeletion(String login, Instant warnedBefore);

    List<Long> findLegacyDeletedUserIds();

    Map<Long, Map<String, Long>> countUserReferences(List<Long> userIds, List<UserReference> references);

    void detachUserReference(String tableName, String columnName, long userId);

    void deleteUserReference(String tableName, String columnName, long userId);

    List<Long> findExclusivelyOwnedTeamIds(long userId);

    List<Long> findOwnedTeamIds(long userId);

    List<Long> findRemainingTeamStudentIds(long teamId, long excludedUserId);

    void replaceTeamOwner(long teamId, @Nullable Long replacementOwnerId);

    void deleteTeamMemberships(long userId);

    void deleteTeam(long teamId);

    List<Long> findParticipationIds(long userId);

    void deleteStudentExams(long userId);

    List<ExamUserImagePaths> findExamUserImagePaths(long userId);

    void deleteExamUsers(long userId);

    void deleteComplaints(long userId);

    void deletePostTreesForPlagiarismCases(long userId);

    void deletePlagiarismCases(long userId);

    void deleteCommunicationContent(long userId);

    void deleteTutorParticipations(long userId);
}
