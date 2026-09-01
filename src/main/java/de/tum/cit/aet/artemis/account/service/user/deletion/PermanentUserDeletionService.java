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

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultStatus;
import de.tum.cit.aet.artemis.account.repository.CustomUserDeletionRepository;
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

    private final CustomUserDeletionRepository userDeletionRepository;

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

    public PermanentUserDeletionService(CustomUserDeletionRepository userDeletionRepository, UserDeletionPlanService userDeletionPlanService,
            AccountCredentialRevocationService accountCredentialRevocationService, ParticipationDeletionService participationDeletionService, DataExportApi dataExportApi,
            Optional<ExamUserApi> examUserApi, Optional<LearnerProfileApi> learnerProfileApi, Optional<ScienceEventApi> scienceEventApi, FileService fileService,
            CustomAuditEventRepository auditEventRepository, @Nullable @Value("${artemis.user-management.internal-admin.username:#{null}}") String internalAdminUsername) {
        this.userDeletionRepository = userDeletionRepository;
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

    public UserDeletionResultDTO deleteByAdmin(long userId, String expectedFingerprint, String actingAdministrator) {
        User user = loadUserForDeletion(userId);
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

    public UserDeletionResultDTO deleteAutomatically(long userId) {
        User user = loadUserForDeletion(userId);
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

    public UserDeletionResultDTO deleteProvisional(long userId) {
        User user = loadUserForDeletion(userId);
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

    private User loadUserForDeletion(long userId) {
        User user = userDeletionRepository.findByIdForDeletion(userId).orElseThrow(() -> new IllegalArgumentException("User " + userId + " does not exist"));
        // The repository fetches authorities and the learner profile because this service intentionally has no
        // transaction boundary. Both associations must remain available while the detached deletion snapshot is used.
        user.getAuthorities().size();
        return user;
    }

    private void delete(User user, UserDeletionImpactDTO impact, UserDeletionMode mode, String actor) {
        long userId = user.getId();
        String login = user.getLogin();
        String imageUrl = user.getImageUrl();
        List<Path> filesToDelete = new ArrayList<>();
        if (userDeletionPlanService.isTableAvailable("data_export")) {
            filesToDelete.addAll(dataExportApi.deleteAllForUser(userId));
        }
        boolean forced = mode == UserDeletionMode.ADMIN_FORCED;
        Long learnerProfileId = user.getLearnerProfile() != null ? user.getLearnerProfile().getId() : null;

        accountCredentialRevocationService.revokeAllCredentials(user, "permanent user deletion");
        if (learnerProfileId != null) {
            // This service has no transaction boundary. Clear the owning foreign key with a focused repository
            // modifying query before deleting the profile instead of relying on a managed User entity.
            userDeletionRepository.clearLearnerProfile(userId);
            learnerProfileApi.ifPresent(api -> api.deleteProfile(learnerProfileId));
        }

        if (forced) {
            detachSharedActorReferences(userId);
            if (userDeletionPlanService.isTableAvailable("team_student")) {
                cleanupTeams(userId);
            }
            if (userDeletionPlanService.isTableAvailable("participation")) {
                cleanupParticipations(userId);
            }
            if (userDeletionPlanService.isTableAvailable("student_exam") || userDeletionPlanService.isTableAvailable("exam_user")) {
                cleanupStudentExams(userId, filesToDelete);
            }
            if (userDeletionPlanService.isTableAvailable("complaint")) {
                cleanupComplaints(userId);
            }
            if (userDeletionPlanService.isTableAvailable("plagiarism_case")) {
                cleanupPlagiarismCases(userId);
            }
            if (userDeletionPlanService.isTableAvailable("post")) {
                cleanupCommunication(userId);
            }
            if (userDeletionPlanService.isTableAvailable("tutor_participation")) {
                cleanupTutorParticipations(userId);
            }
        }

        executeDirectReferencePolicies(userId, forced);

        int deleted = userDeletionRepository.deleteUserRow(userId);
        if (deleted != 1) {
            throw new IllegalStateException("Expected to delete one user row, deleted " + deleted);
        }

        // Science events store the login as an identity rather than a foreign key. Rename it once the user row is gone.
        scienceEventApi.ifPresent(api -> api.renameIdentity(login, "deleted-user-" + userId));
        auditEventRepository.add(new AuditEvent(actor, AUDIT_EVENT_TYPE,
                Map.of("targetUserId", userId, "mode", mode.name(), "affectedObjects", impact.totalAffectedObjects(), "outcome", UserDeletionResultStatus.DELETED.name())));
        scheduleExternalCleanup(imageUrl, filesToDelete);
    }

    private void detachSharedActorReferences(long userId) {
        for (UserDeletionReferencePolicy policy : userDeletionPlanService.availablePolicies()) {
            if (policy.action() == UserDeletionAction.DETACH_ACTOR && policy != UserDeletionReferencePolicy.TEAM_OWNER) {
                userDeletionRepository.detachUserReference(policy.tableName(), policy.columnName(), userId);
            }
        }
    }

    private void cleanupTeams(long userId) {
        List<Long> exclusivelyOwnedTeamIds = userDeletionRepository.findExclusivelyOwnedTeamIds(userId);

        List<Long> ownedTeamIds = userDeletionRepository.findOwnedTeamIds(userId);
        for (Long teamId : ownedTeamIds) {
            List<Long> remainingStudents = userDeletionRepository.findRemainingTeamStudentIds(teamId, userId);
            Long replacementOwner = remainingStudents.isEmpty() ? null : remainingStudents.getFirst();
            userDeletionRepository.replaceTeamOwner(teamId, replacementOwner);
        }

        userDeletionRepository.deleteTeamMemberships(userId);
        for (Long teamId : exclusivelyOwnedTeamIds) {
            participationDeletionService.deleteAllByTeamId(teamId);
            userDeletionRepository.deleteTeam(teamId);
        }
    }

    private void cleanupParticipations(long userId) {
        List<Long> participationIds = userDeletionRepository.findParticipationIds(userId);
        participationIds.forEach(participationId -> participationDeletionService.delete(participationId, true));
    }

    private void cleanupStudentExams(long userId, List<Path> filesToDeleteAfterCommit) {
        userDeletionRepository.deleteStudentExams(userId);
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
        userDeletionRepository.findExamUserImagePaths(userId).forEach(paths -> {
            addExamUserImagePath(imagePaths, paths.signingImagePath(), FilePathType.EXAM_USER_SIGNATURE);
            addExamUserImagePath(imagePaths, paths.studentImagePath(), FilePathType.EXAM_USER_IMAGE);
        });
        userDeletionRepository.deleteExamUsers(userId);
        return imagePaths;
    }

    private void addExamUserImagePath(List<Path> imagePaths, @Nullable String imageUri, FilePathType filePathType) {
        if (imageUri != null) {
            imagePaths.add(FilePathConverter.fileSystemPathForExternalUri(URI.create(imageUri), filePathType));
        }
    }

    private void cleanupComplaints(long userId) {
        userDeletionRepository.deleteComplaints(userId);
    }

    private void cleanupPlagiarismCases(long userId) {
        deletePostTreesForPlagiarismCases(userId);
        userDeletionRepository.deletePlagiarismCases(userId);
    }

    private void deletePostTreesForPlagiarismCases(long userId) {
        userDeletionRepository.deletePostTreesForPlagiarismCases(userId);
    }

    private void cleanupCommunication(long userId) {
        userDeletionRepository.deleteCommunicationContent(userId);
    }

    private void cleanupTutorParticipations(long userId) {
        userDeletionRepository.deleteTutorParticipations(userId);
    }

    private void executeDirectReferencePolicies(long userId, boolean forced) {
        for (UserDeletionReferencePolicy policy : userDeletionPlanService.availablePolicies()) {
            if (!forced && policy.automaticBlocker()) {
                continue;
            }
            if (policy.action() == UserDeletionAction.DETACH_ACTOR) {
                userDeletionRepository.detachUserReference(policy.tableName(), policy.columnName(), userId);
            }
            else {
                userDeletionRepository.deleteUserReference(policy.tableName(), policy.columnName(), userId);
            }
        }
    }

    private void scheduleExternalCleanup(@Nullable String imageUrl, List<Path> filesToDelete) {
        // All database modifications above are completed by repository-owned transactions before external cleanup is
        // scheduled. This service deliberately has no transaction boundary.
        filesToDelete.forEach(path -> fileService.schedulePathForDeletion(path, 0));
        if (imageUrl != null) {
            fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(URI.create(imageUrl), FilePathType.PROFILE_PICTURE), 0);
        }
    }

    private boolean isAlwaysProtected(User user) {
        return user.isBot() || IRIS_BOT_LOGIN.equals(user.getLogin()) || Objects.equals(internalAdminUsername, user.getLogin());
    }

    private UserDeletionResultDTO result(User user, UserDeletionResultStatus status, @Nullable String reason) {
        return new UserDeletionResultDTO(user.getId(), user.getLogin(), status, reason);
    }
}
