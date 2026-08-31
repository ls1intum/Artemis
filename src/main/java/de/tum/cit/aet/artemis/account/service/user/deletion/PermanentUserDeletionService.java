package de.tum.cit.aet.artemis.account.service.user.deletion;

import static de.tum.cit.aet.artemis.account.domain.User.IRIS_BOT_LOGIN;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultStatus;
import de.tum.cit.aet.artemis.account.service.AccountCredentialRevocationService;
import de.tum.cit.aet.artemis.admin.api.DataExportApi;
import de.tum.cit.aet.artemis.admin.repository.CustomAuditEventRepository;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.ScienceEventApi;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exam.api.ExamUserApi;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;

/**
 * Physically deletes a user after applying the plan that was previewed. Business-domain cleanup is deliberately
 * explicit; a final foreign-key check is the safety net for references not yet represented in the policy registry.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class PermanentUserDeletionService {

    private static final String AUDIT_EVENT_TYPE = "USER_PERMANENTLY_DELETED";

    private final EntityManager entityManager;

    private final JdbcTemplate jdbcTemplate;

    private final UserDeletionPlanService userDeletionPlanService;

    private final AccountCredentialRevocationService accountCredentialRevocationService;

    private final ParticipationDeletionService participationDeletionService;

    private final DataExportApi dataExportApi;

    private final Optional<ExamUserApi> examUserApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<ScienceEventApi> scienceEventApi;

    private final FileService fileService;

    private final CustomAuditEventRepository auditEventRepository;

    private final String internalAdminUsername;

    public PermanentUserDeletionService(EntityManager entityManager, JdbcTemplate jdbcTemplate, UserDeletionPlanService userDeletionPlanService,
            AccountCredentialRevocationService accountCredentialRevocationService, ParticipationDeletionService participationDeletionService, DataExportApi dataExportApi,
            Optional<ExamUserApi> examUserApi, Optional<LearnerProfileApi> learnerProfileApi, Optional<ScienceEventApi> scienceEventApi, FileService fileService,
            CustomAuditEventRepository auditEventRepository, @Nullable @Value("${artemis.user-management.internal-admin.username:#{null}}") String internalAdminUsername) {
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.userDeletionPlanService = userDeletionPlanService;
        this.accountCredentialRevocationService = accountCredentialRevocationService;
        this.participationDeletionService = participationDeletionService;
        this.dataExportApi = dataExportApi;
        this.examUserApi = examUserApi;
        this.learnerProfileApi = learnerProfileApi;
        this.scienceEventApi = scienceEventApi;
        this.fileService = fileService;
        this.auditEventRepository = auditEventRepository;
        this.internalAdminUsername = internalAdminUsername;
    }

    @Transactional
    public UserDeletionResultDTO deleteByAdmin(long userId, String expectedFingerprint, String actingAdministrator) {
        User user = lockUser(userId);
        if (isAlwaysProtected(user) || user.getLogin().equals(actingAdministrator)) {
            return result(user, UserDeletionResultStatus.FORBIDDEN, "protectedUser");
        }
        UserDeletionImpactDTO impact = userDeletionPlanService.createImpact(user, UserDeletionMode.ADMIN_FORCED);
        if (!Objects.equals(expectedFingerprint, impact.impactFingerprint())) {
            return result(user, UserDeletionResultStatus.PLAN_CHANGED, "impactChanged");
        }
        delete(user, impact, UserDeletionMode.ADMIN_FORCED, actingAdministrator);
        return result(user, UserDeletionResultStatus.DELETED, null);
    }

    @Transactional
    public UserDeletionResultDTO deleteAutomatically(long userId) {
        User user = lockUser(userId);
        if (isAlwaysProtected(user) || AuthorizationCheckService.isAdmin(user.getAuthorities())) {
            return result(user, UserDeletionResultStatus.FORBIDDEN, "protectedUser");
        }
        UserDeletionImpactDTO impact = userDeletionPlanService.createImpact(user, UserDeletionMode.AUTOMATIC);
        if (!impact.automaticEligible()) {
            return result(user, UserDeletionResultStatus.BLOCKED, "remainingReferences");
        }
        delete(user, impact, UserDeletionMode.AUTOMATIC, "system");
        return result(user, UserDeletionResultStatus.DELETED, null);
    }

    @Transactional
    public UserDeletionResultDTO deleteProvisional(long userId) {
        User user = lockUser(userId);
        if (user.getActivated() || user.isDeleted() || isAlwaysProtected(user)) {
            return result(user, UserDeletionResultStatus.BLOCKED, "registrationStateChanged");
        }
        UserDeletionImpactDTO impact = userDeletionPlanService.createImpact(user, UserDeletionMode.PROVISIONAL);
        if (!impact.automaticEligible()) {
            return result(user, UserDeletionResultStatus.BLOCKED, "remainingReferences");
        }
        delete(user, impact, UserDeletionMode.PROVISIONAL, "system");
        return result(user, UserDeletionResultStatus.DELETED, null);
    }

    private User lockUser(long userId) {
        User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) {
            throw new IllegalArgumentException("User " + userId + " does not exist");
        }
        user.getAuthorities().size();
        return user;
    }

    private void delete(User user, UserDeletionImpactDTO impact, UserDeletionMode mode, String actor) {
        long userId = user.getId();
        String login = user.getLogin();
        String imageUrl = user.getImageUrl();
        List<Path> filesToDeleteAfterCommit = new ArrayList<>(dataExportApi.deleteAllForUser(userId));
        boolean forced = mode == UserDeletionMode.ADMIN_FORCED;

        accountCredentialRevocationService.revokeAllCredentials(user, "permanent user deletion");
        learnerProfileApi.ifPresent(api -> api.deleteProfile(user));
        user.setLearnerProfile(null);

        if (forced) {
            detachSharedActorReferences(userId);
            cleanupTeams(userId);
            cleanupParticipations(userId);
            cleanupStudentExams(userId, filesToDeleteAfterCommit);
            cleanupComplaints(userId);
            cleanupPlagiarismCases(userId);
            cleanupCommunication(userId);
            cleanupTutorParticipations(userId);
        }

        entityManager.flush();
        executeDirectReferencePolicies(userId, forced);
        entityManager.flush();
        entityManager.detach(user);

        int deleted = jdbcTemplate.update("DELETE FROM jhi_user WHERE id = ?", userId);
        if (deleted != 1) {
            throw new IllegalStateException("Expected to delete one user row, deleted " + deleted);
        }

        auditEventRepository.add(new AuditEvent(actor, AUDIT_EVENT_TYPE,
                Map.of("targetUserId", userId, "mode", mode.name(), "affectedObjects", impact.totalAffectedObjects(), "outcome", UserDeletionResultStatus.DELETED.name())));
        scheduleExternalCleanupAfterCommit(userId, login, imageUrl, filesToDeleteAfterCommit);
    }

    private void detachSharedActorReferences(long userId) {
        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            if (policy.action() == UserDeletionAction.DETACH_ACTOR && policy != UserDeletionReferencePolicy.TEAM_OWNER) {
                jdbcTemplate.update("UPDATE " + policy.tableName() + " SET " + policy.columnName() + " = NULL WHERE " + policy.columnName() + " = ?", userId);
            }
        }
    }

    private void cleanupTeams(long userId) {
        List<Long> exclusivelyOwnedTeamIds = jdbcTemplate.queryForList("""
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

        List<Long> ownedTeamIds = jdbcTemplate.queryForList("SELECT id FROM team WHERE owner_id = ?", Long.class, userId);
        for (Long teamId : ownedTeamIds) {
            List<Long> remainingStudents = jdbcTemplate.queryForList("SELECT student_id FROM team_student WHERE team_id = ? AND student_id <> ? ORDER BY student_id", Long.class,
                    teamId, userId);
            Long replacementOwner = remainingStudents.isEmpty() ? null : remainingStudents.getFirst();
            jdbcTemplate.update("UPDATE team SET owner_id = ? WHERE id = ?", replacementOwner, teamId);
        }

        jdbcTemplate.update("DELETE FROM team_student WHERE student_id = ?", userId);
        for (Long teamId : exclusivelyOwnedTeamIds) {
            participationDeletionService.deleteAllByTeamId(teamId);
            jdbcTemplate.update("DELETE FROM team WHERE id = ?", teamId);
        }
    }

    private void cleanupParticipations(long userId) {
        List<Long> participationIds = jdbcTemplate.queryForList("SELECT id FROM participation WHERE student_id = ? ORDER BY id", Long.class, userId);
        participationIds.forEach(participationId -> participationDeletionService.delete(participationId, true));
    }

    private void cleanupStudentExams(long userId, List<Path> filesToDeleteAfterCommit) {
        jdbcTemplate.update("DELETE FROM exam_session WHERE student_exam_id IN (SELECT id FROM student_exam WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM student_exam_exercise WHERE student_exam_id IN (SELECT id FROM student_exam WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM student_exam WHERE user_id = ?", userId);
        if (examUserApi.isPresent()) {
            filesToDeleteAfterCommit.addAll(examUserApi.orElseThrow().deleteAllForUser(userId));
        }
        else {
            filesToDeleteAfterCommit.addAll(cleanupExamUsersWithoutExamProfile(userId));
        }
    }

    /**
     * The exam service and repository are conditional beans. The tables and their personal files can nevertheless
     * still exist when the exam profile is disabled, so account deletion must retain a profile-independent fallback.
     */
    private List<Path> cleanupExamUsersWithoutExamProfile(long userId) {
        List<Path> imagePaths = new ArrayList<>();
        jdbcTemplate.query("SELECT signing_image_path, student_image_path FROM exam_user WHERE student_id = ?", resultSet -> {
            addExamUserImagePath(imagePaths, resultSet.getString("signing_image_path"), FilePathType.EXAM_USER_SIGNATURE);
            addExamUserImagePath(imagePaths, resultSet.getString("student_image_path"), FilePathType.EXAM_USER_IMAGE);
        }, userId);
        jdbcTemplate.update("DELETE FROM exam_user WHERE student_id = ?", userId);
        return imagePaths;
    }

    private void addExamUserImagePath(List<Path> imagePaths, @Nullable String imageUri, FilePathType filePathType) {
        if (imageUri != null) {
            imagePaths.add(FilePathConverter.fileSystemPathForExternalUri(URI.create(imageUri), filePathType));
        }
    }

    private void cleanupComplaints(long userId) {
        jdbcTemplate.update("DELETE FROM complaint_response WHERE complaint_id IN (SELECT id FROM complaint WHERE student_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM complaint WHERE student_id = ?", userId);
    }

    private void cleanupPlagiarismCases(long userId) {
        deletePostTreesForPlagiarismCases(userId);
        jdbcTemplate.update("UPDATE plagiarism_submission SET plagiarism_case_id = NULL WHERE plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM plagiarism_case WHERE student_id = ?", userId);
    }

    private void deletePostTreesForPlagiarismCases(long userId) {
        jdbcTemplate.update("""
                DELETE FROM reaction
                WHERE answer_post_id IN (
                    SELECT answer.id FROM answer_post answer
                    WHERE answer.post_id IN (
                        SELECT post.id FROM post post
                        WHERE post.plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)
                    )
                )
                """, userId);
        jdbcTemplate.update("""
                DELETE FROM answer_post
                WHERE post_id IN (
                    SELECT post.id FROM post post
                    WHERE post.plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)
                )
                """, userId);
        jdbcTemplate.update("""
                DELETE FROM reaction
                WHERE post_id IN (
                    SELECT post.id FROM post post
                    WHERE post.plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)
                )
                """, userId);
        jdbcTemplate.update("DELETE FROM post WHERE plagiarism_case_id IN (SELECT id FROM plagiarism_case WHERE student_id = ?)", userId);
    }

    private void cleanupCommunication(long userId) {
        jdbcTemplate.update("""
                DELETE FROM reaction
                WHERE answer_post_id IN (
                    SELECT answer.id FROM answer_post answer
                    WHERE answer.author_id = ? OR answer.post_id IN (SELECT post.id FROM post post WHERE post.author_id = ?)
                )
                """, userId, userId);
        jdbcTemplate.update("DELETE FROM answer_post WHERE author_id = ? OR post_id IN (SELECT id FROM post WHERE author_id = ?)", userId, userId);
        jdbcTemplate.update("DELETE FROM reaction WHERE post_id IN (SELECT id FROM post WHERE author_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM post WHERE author_id = ?", userId);
        jdbcTemplate.update("DELETE FROM reaction WHERE user_id = ?", userId);
    }

    private void cleanupTutorParticipations(long userId) {
        jdbcTemplate.update("""
                DELETE FROM tutor_participation_trained_example_submissions
                WHERE tutor_participation_id IN (SELECT id FROM tutor_participation WHERE tutor_id = ?)
                """, userId);
        jdbcTemplate.update("DELETE FROM tutor_participation WHERE tutor_id = ?", userId);
    }

    private void executeDirectReferencePolicies(long userId, boolean forced) {
        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            if (!forced && policy.automaticBlocker()) {
                continue;
            }
            if (policy.action() == UserDeletionAction.DETACH_ACTOR) {
                jdbcTemplate.update("UPDATE " + policy.tableName() + " SET " + policy.columnName() + " = NULL WHERE " + policy.columnName() + " = ?", userId);
            }
            else {
                jdbcTemplate.update("DELETE FROM " + policy.tableName() + " WHERE " + policy.columnName() + " = ?", userId);
            }
        }
    }

    private void scheduleExternalCleanupAfterCommit(long userId, String login, @Nullable String imageUrl, List<Path> filesToDelete) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                filesToDelete.forEach(path -> fileService.schedulePathForDeletion(path, 0));
                if (imageUrl != null) {
                    fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(URI.create(imageUrl), FilePathType.PROFILE_PICTURE), 0);
                }
                scienceEventApi.ifPresent(api -> api.renameIdentity(login, "deleted-user-" + userId));
            }
        });
    }

    private boolean isAlwaysProtected(User user) {
        return user.isBot() || IRIS_BOT_LOGIN.equals(user.getLogin()) || Objects.equals(internalAdminUsername, user.getLogin());
    }

    private UserDeletionResultDTO result(User user, UserDeletionResultStatus status, @Nullable String reason) {
        return new UserDeletionResultDTO(user.getId(), user.getLogin(), status, reason);
    }
}
