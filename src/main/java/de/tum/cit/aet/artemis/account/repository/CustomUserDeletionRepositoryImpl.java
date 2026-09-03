package de.tum.cit.aet.artemis.account.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.User;

/** Spring Data implementation of the permanent user deletion repository fragment. */
@Repository
public class CustomUserDeletionRepositoryImpl implements CustomUserDeletionRepository {

    private final UserRepository userRepository;

    private final DataSource dataSource;

    private final JdbcClient jdbcClient;

    private volatile Set<String> availableTableNames;

    public CustomUserDeletionRepositoryImpl(UserRepository userRepository, DataSource dataSource) {
        this.userRepository = userRepository;
        this.dataSource = dataSource;
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    @Override
    public Optional<User> findByIdForDeletion(long userId) {
        return userRepository.findByIdForDeletion(userId);
    }

    @Override
    public void clearLearnerProfile(long userId) {
        userRepository.clearLearnerProfileForDeletion(userId);
    }

    @Override
    public int deleteUserRow(long userId) {
        return userRepository.deleteUserRow(userId);
    }

    @Override
    public int claimUnactivatedUserForDeletion(long userId) {
        return userRepository.claimUnactivatedUserForDeletion(userId);
    }

    @Override
    public Set<String> findAvailableTableNames() {
        Set<String> cached = availableTableNames;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (availableTableNames == null) {
                try (var connection = dataSource.getConnection()) {
                    Set<String> tables = new HashSet<>();
                    try (ResultSet resultSet = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), "%", new String[] { "TABLE" })) {
                        while (resultSet.next()) {
                            tables.add(resultSet.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                        }
                    }
                    availableTableNames = Set.copyOf(tables);
                }
                catch (SQLException exception) {
                    throw new DataAccessResourceFailureException("Could not inspect database tables", exception);
                }
            }
            return availableTableNames;
        }
    }

    @Override
    public List<Long> findLegacyDeletedUserIds() {
        return queryForList("SELECT id FROM jhi_user WHERE is_deleted = TRUE", Long.class);
    }

    @Override
    public Map<Long, Map<String, Long>> countUserReferences(List<Long> userIds, List<UserReference> references) {
        Map<Long, Map<String, Long>> result = new LinkedHashMap<>();
        userIds.forEach(userId -> result.put(userId, new LinkedHashMap<>()));
        if (userIds.isEmpty() || references.isEmpty()) {
            return result;
        }

        if (userIds.size() == 1) {
            long userId = userIds.getFirst();
            List<String> statements = new ArrayList<>();
            Object[] parameters = new Object[references.size()];
            int index = 0;
            for (UserReference reference : references) {
                statements.add("SELECT '" + reference.policyName() + "' AS policy_name, COUNT(*) AS reference_count FROM " + reference.tableName() + " WHERE "
                        + reference.columnName() + " = ?");
                parameters[index++] = userId;
            }
            jdbcClient.sql(String.join(" UNION ALL ", statements)).params(parameters).query(resultSet -> {
                while (resultSet.next()) {
                    result.get(userId).put(resultSet.getString("policy_name"), resultSet.getLong("reference_count"));
                }
                return result;
            });
            return result;
        }

        String placeholders = String.join(", ", userIds.stream().map(ignored -> "?").toList());
        Object[] parameters = userIds.toArray();
        for (UserReference reference : references) {
            String sql = "SELECT " + reference.columnName() + ", COUNT(*) FROM " + reference.tableName() + " WHERE " + reference.columnName() + " IN (" + placeholders
                    + ") GROUP BY " + reference.columnName();
            jdbcClient.sql(sql).params(parameters).query(resultSet -> {
                while (resultSet.next()) {
                    result.get(resultSet.getLong(1)).put(reference.policyName(), resultSet.getLong(2));
                }
                return result;
            });
        }
        return result;
    }

    @Override
    @Transactional // ok because of modifying query
    public void detachUserReference(String tableName, String columnName, long userId) {
        update("UPDATE " + tableName + " SET " + columnName + " = NULL WHERE " + columnName + " = ?", userId);
    }

    @Override
    @Transactional // ok because of delete
    public void deleteUserReference(String tableName, String columnName, long userId) {
        update("DELETE FROM " + tableName + " WHERE " + columnName + " = ?", userId);
    }

    @Override
    public List<Long> findExclusivelyOwnedTeamIds(long userId) {
        return queryForList("""
                SELECT membership.team_id
                FROM team_student membership
                WHERE membership.student_id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM team_student other_membership
                      WHERE other_membership.team_id = membership.team_id
                        AND other_membership.student_id <> ?
                  )
                """, Long.class, userId, userId);
    }

    @Override
    public List<Long> findOwnedTeamIds(long userId) {
        return queryForList("SELECT id FROM team WHERE owner_id = ?", Long.class, userId);
    }

    @Override
    public List<Long> findRemainingTeamStudentIds(long teamId, long excludedUserId) {
        return queryForList("SELECT student_id FROM team_student WHERE team_id = ? AND student_id <> ? ORDER BY student_id", Long.class, teamId, excludedUserId);
    }

    @Override
    @Transactional // ok because of modifying query
    public void replaceTeamOwner(long teamId, @Nullable Long replacementOwnerId) {
        update("UPDATE team SET owner_id = ? WHERE id = ?", replacementOwnerId, teamId);
    }

    @Override
    @Transactional // ok because of delete
    public void deleteTeamMemberships(long userId) {
        update("DELETE FROM team_student WHERE student_id = ?", userId);
    }

    @Override
    @Transactional // ok because of delete
    public void deleteTeam(long teamId) {
        update("DELETE FROM team WHERE id = ?", teamId);
    }

    @Override
    public List<Long> findParticipationIds(long userId) {
        return queryForList("SELECT id FROM participation WHERE student_id = ? ORDER BY id", Long.class, userId);
    }

    @Override
    @Transactional // ok because of delete
    public void deleteStudentExams(long userId) {
        update("DELETE FROM exam_session WHERE student_exam_id IN (SELECT id FROM student_exam WHERE user_id = ?)", userId);
        update("DELETE FROM student_exam_exercise WHERE student_exam_id IN (SELECT id FROM student_exam WHERE user_id = ?)", userId);
        update("DELETE FROM student_exam WHERE user_id = ?", userId);
    }

    @Override
    public List<ExamUserImagePaths> findExamUserImagePaths(long userId) {
        return jdbcClient.sql("SELECT signing_image_path, student_image_path FROM exam_user WHERE student_id = ?").param(userId)
                .query((resultSet, rowNumber) -> new ExamUserImagePaths(resultSet.getString("signing_image_path"), resultSet.getString("student_image_path"))).list();
    }

    @Override
    @Transactional // ok because of delete
    public void deleteExamUsers(long userId) {
        update("DELETE FROM exam_user WHERE student_id = ?", userId);
    }

    @Override
    @Transactional // ok because of delete
    public void deleteComplaints(long userId) {
        update("DELETE FROM complaint_response WHERE complaint_id IN (SELECT id FROM complaint WHERE student_id = ?)", userId);
        update("DELETE FROM complaint WHERE student_id = ?", userId);
    }

    @Override
    @Transactional // ok because of delete
    public void deletePostTreesForPlagiarismCases(long userId) {
        update("""
                DELETE FROM reaction
                WHERE answer_post_id IN (
                    SELECT answer.id FROM answer_post answer
                    WHERE answer.post_id IN (
                        SELECT post.id FROM post post
                        WHERE post.plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)
                    )
                )
                """, userId);
        update("""
                DELETE FROM answer_post
                WHERE post_id IN (
                    SELECT post.id FROM post post
                    WHERE post.plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)
                )
                """, userId);
        update("""
                DELETE FROM reaction
                WHERE post_id IN (
                    SELECT post.id FROM post post
                    WHERE post.plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)
                )
                """, userId);
        update("DELETE FROM post WHERE plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)", userId);
    }

    @Override
    @Transactional // ok because of delete
    public void deletePlagiarismCases(long userId) {
        update("UPDATE plagiarism_submission SET plagiarism_case_id = NULL WHERE plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)", userId);
        update("DELETE FROM plagiarism_case WHERE student_id = ?", userId);
    }

    @Override
    @Transactional // ok because of delete
    public void deleteCommunicationContent(long userId) {
        update("""
                DELETE FROM reaction
                WHERE answer_post_id IN (
                    SELECT answer.id FROM answer_post answer
                    WHERE answer.author_id = ? OR answer.post_id IN (SELECT post.id FROM post post WHERE post.author_id = ?)
                )
                """, userId, userId);
        update("DELETE FROM answer_post WHERE author_id = ? OR post_id IN (SELECT id FROM post WHERE author_id = ?)", userId, userId);
        update("DELETE FROM reaction WHERE post_id IN (SELECT id FROM post WHERE author_id = ?)", userId);
        update("DELETE FROM post WHERE author_id = ?", userId);
        update("DELETE FROM reaction WHERE user_id = ?", userId);
    }

    @Override
    @Transactional // ok because of delete
    public void deleteTutorParticipations(long userId) {
        update("""
                DELETE FROM tutor_participation_trained_example_submissions
                WHERE tutor_participation_id IN (SELECT id FROM tutor_participation WHERE tutor_id = ?)
                """, userId);
        update("DELETE FROM tutor_participation WHERE tutor_id = ?", userId);
    }

    private int update(String sql, Object... parameters) {
        return jdbcClient.sql(sql).params(parameters).update();
    }

    private <T> List<T> queryForList(String sql, Class<T> elementType, Object... parameters) {
        return jdbcClient.sql(sql).params(parameters).query(elementType).list();
    }
}
