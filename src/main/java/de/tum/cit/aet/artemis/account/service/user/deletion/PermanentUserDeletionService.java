package de.tum.cit.aet.artemis.account.service.user.deletion;

import static de.tum.cit.aet.artemis.account.domain.User.IRIS_BOT_LOGIN;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.AccountCredentialRevocationService;
import de.tum.cit.aet.artemis.admin.repository.CustomAuditEventRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;

/**
 * Physically deletes a user after applying the plan that was previewed. Business-domain cleanup is deliberately
 * explicit; a final foreign-key check is the safety net for references not yet represented in the policy registry.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class PermanentUserDeletionService {

    private static final String AUDIT_EVENT_TYPE = "USER_PERMANENTLY_DELETED";

    private final UserRepository userRepository;

    private final UserDeletionPlanService userDeletionPlanService;

    private final UserReferenceCleanupService userReferenceCleanupService;

    private final UserOwnedContentDeletionService userOwnedContentDeletionService;

    private final AccountCredentialRevocationService accountCredentialRevocationService;

    private final FileService fileService;

    private final CustomAuditEventRepository auditEventRepository;

    private final String internalAdminUsername;

    public PermanentUserDeletionService(UserRepository userRepository, UserDeletionPlanService userDeletionPlanService, UserReferenceCleanupService userReferenceCleanupService,
            UserOwnedContentDeletionService userOwnedContentDeletionService, AccountCredentialRevocationService accountCredentialRevocationService, FileService fileService,
            CustomAuditEventRepository auditEventRepository, @Nullable @Value("${artemis.user-management.internal-admin.username:#{null}}") String internalAdminUsername) {
        this.userRepository = userRepository;
        this.userDeletionPlanService = userDeletionPlanService;
        this.userReferenceCleanupService = userReferenceCleanupService;
        this.userOwnedContentDeletionService = userOwnedContentDeletionService;
        this.accountCredentialRevocationService = accountCredentialRevocationService;
        this.fileService = fileService;
        this.auditEventRepository = auditEventRepository;
        this.internalAdminUsername = internalAdminUsername;
    }

    /**
     * Deletes an account on an administrator's instruction, after confirming that the previewed impact still holds.
     *
     * @param userId              the account to delete
     * @param expectedFingerprint the fingerprint of the impact the administrator confirmed
     * @param actingAdministrator the login of the administrator, recorded in the audit event
     * @return what happened: deleted, forbidden for a protected account or the caller themselves, or the plan changed
     */
    public UserDeletionResultDTO deleteByAdmin(long userId, String expectedFingerprint, String actingAdministrator) {
        User user = loadUserForDeletion(userId);
        if (isProtectedFromPermanentDeletion(user) || user.getLogin().equals(actingAdministrator)) {
            return result(user, UserDeletionResultStatus.FORBIDDEN, "protectedUser");
        }
        UserDeletionImpactDTO impact = userDeletionPlanService.createImpact(user, UserDeletionMode.ADMIN_FORCED);
        if (!Objects.equals(expectedFingerprint, impact.impactFingerprint())) {
            return result(user, UserDeletionResultStatus.PLAN_CHANGED, "impactChanged");
        }
        delete(user, impact, UserDeletionMode.ADMIN_FORCED, actingAdministrator);
        return result(user, UserDeletionResultStatus.DELETED, null);
    }

    /**
     * Deletes an account the retention policy selected, without an administrator confirming anything. Only an account
     * whose remaining references are all deletable by policy is removed. The due-date, activity, enrollment, authority,
     * and reference conditions are re-checked immediately before cleanup begins.
     *
     * @param userId       the account to delete
     * @param warnedBefore only a warning sent before this instant has completed its grace period
     * @return what happened: deleted, forbidden for a protected or administrator account, or blocked by a reference
     */
    public UserDeletionResultDTO deleteAutomatically(long userId, Instant warnedBefore) {
        User user = loadUserForDeletion(userId);
        if (isProtectedFromPermanentDeletion(user)) {
            return result(user, UserDeletionResultStatus.FORBIDDEN, "protectedUser");
        }
        if (userRepository.countNotEnrolledUserStillDueForDeletion(user.getLogin(), warnedBefore) != 1) {
            return result(user, UserDeletionResultStatus.BLOCKED, "noLongerEligible");
        }
        return deleteIfReferencesAllow(user, UserDeletionMode.AUTOMATIC);
    }

    /**
     * Purges a legacy tombstone once all business-domain references have disappeared.
     *
     * @param userId the legacy tombstone to purge
     * @return what happened: deleted, forbidden for a protected or administrator account, or blocked
     */
    public UserDeletionResultDTO deleteLegacyTombstone(long userId) {
        User user = loadUserForDeletion(userId);
        if (!user.isDeleted()) {
            return result(user, UserDeletionResultStatus.BLOCKED, "notLegacyDeleted");
        }
        if (isProtectedFromPermanentDeletion(user)) {
            return result(user, UserDeletionResultStatus.FORBIDDEN, "protectedUser");
        }
        return deleteIfReferencesAllow(user, UserDeletionMode.AUTOMATIC);
    }

    /**
     * Deletes a registration that was never completed. The account has to be unactivated when it is evaluated for
     * cleanup.
     *
     * @param userId the account to delete
     * @return what happened: deleted, forbidden for a protected or administrator account, or blocked because the account
     *         was activated, already deleted or has references
     */
    public UserDeletionResultDTO deleteProvisional(long userId) {
        User user = loadUserForDeletion(userId);
        if (isProtectedFromPermanentDeletion(user)) {
            return result(user, UserDeletionResultStatus.FORBIDDEN, "protectedUser");
        }
        if (user.getActivated() || user.isDeleted()) {
            return result(user, UserDeletionResultStatus.BLOCKED, "registrationStateChanged");
        }
        return deleteIfReferencesAllow(user, UserDeletionMode.PROVISIONAL);
    }

    private User loadUserForDeletion(long userId) {
        User user = userRepository.findByIdForDeletion(userId).orElseThrow(() -> new IllegalArgumentException("User " + userId + " does not exist"));
        // The repository fetches both associations because this service deliberately has no transaction boundary.
        user.getAuthorities().size();
        user.getLearnerProfile();
        return user;
    }

    private UserDeletionResultDTO deleteIfReferencesAllow(User user, UserDeletionMode mode) {
        UserDeletionImpactDTO impact = userDeletionPlanService.createImpact(user, mode);
        if (!impact.automaticEligible()) {
            return result(user, UserDeletionResultStatus.BLOCKED, "remainingReferences");
        }
        delete(user, impact, mode, "system");
        return result(user, UserDeletionResultStatus.DELETED, null);
    }

    private void delete(User user, UserDeletionImpactDTO impact, UserDeletionMode mode, String actor) {
        long userId = user.getId();
        String login = user.getLogin();
        closeAccount(userId);

        String imageUrl = user.getImageUrl();
        List<Path> filesToDelete = new ArrayList<>();
        if (imageUrl != null) {
            filesToDelete.add(new FileSystemLocation.ProfilePicture(imageUrl).path());
        }
        filesToDelete.addAll(userOwnedContentDeletionService.deleteDataExports(userId));
        boolean forced = mode == UserDeletionMode.ADMIN_FORCED;

        accountCredentialRevocationService.revokeAllCredentials(user, "permanent user deletion");
        if (user.getLearnerProfile() != null) {
            userOwnedContentDeletionService.deleteLearnerProfile(userId, user.getLearnerProfile().getId());
        }

        if (forced) {
            detachSharedActorReferences(userId);
            userOwnedContentDeletionService.deleteTeams(userId);
            userOwnedContentDeletionService.deleteParticipations(userId);
            filesToDelete.addAll(userOwnedContentDeletionService.deleteExamAttendance(userId));
            userOwnedContentDeletionService.deleteComplaints(userId);
            userOwnedContentDeletionService.deletePlagiarismCases(userId);
            userOwnedContentDeletionService.deleteCommunicationContent(userId);
            userOwnedContentDeletionService.deleteTutorParticipations(userId);
        }

        resolveDirectReferences(userId, forced);

        int deleted = userRepository.deleteUserRow(userId);
        if (deleted != 1) {
            throw new IllegalStateException("Expected to delete one user row, deleted " + deleted);
        }

        userOwnedContentDeletionService.anonymiseScienceEvents(login, userId);
        auditEventRepository.add(new AuditEvent(actor, AUDIT_EVENT_TYPE,
                Map.of("targetUserId", userId, "mode", mode.name(), "affectedObjects", impact.totalAffectedObjects(), "outcome", UserDeletionResultStatus.DELETED.name())));
        scheduleExternalCleanup(filesToDelete);
    }

    /**
     * Takes the account out of use before anything is removed. A deactivated account is refused by every authentication
     * provider - password, LDAP, SAML2, OIDC, passkey - and by git over HTTPS and SSH, and dropping its course
     * memberships removes what it could still reach. A session that is already signed in keeps its token until it
     * expires, since a JWT is validated from its claims alone, but it can no longer be renewed.
     */
    private void closeAccount(long userId) {
        userRepository.deactivateForDeletion(userId);
        userReferenceCleanupService.resolve(UserDeletionReferencePolicy.COURSE_ROLE, userId);
    }

    /**
     * Releases the account from other people's work before its own is taken down. What it did as an assessor, a
     * reviewer or a verifier is only detached, because that work belongs to whoever it was done for.
     *
     * <p>
     * Team ownership is the exception: the teams the account owns are found through that reference, so it is resolved
     * later, once they have been handed over.
     */
    private void detachSharedActorReferences(long userId) {
        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            if (policy.action() == UserDeletionAction.DETACH_ACTOR && policy != UserDeletionReferencePolicy.TEAM_OWNER) {
                userReferenceCleanupService.resolve(policy, userId);
            }
        }
    }

    /**
     * Resolves every direct reference to the account, so that its row can be removed. In a deletion the retention
     * policy started rather than an administrator, business-domain references are left alone: such a deletion only
     * happens once their counts are zero, and touching them would remove data the policy is not allowed to remove.
     */
    private void resolveDirectReferences(long userId, boolean forced) {
        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            if (forced || !policy.automaticBlocker()) {
                userReferenceCleanupService.resolve(policy, userId);
            }
        }
    }

    private void scheduleExternalCleanup(List<Path> filesToDelete) {
        // All database modifications above are completed by repository-owned transactions before external cleanup is
        // scheduled. This service deliberately has no transaction boundary.
        filesToDelete.forEach(path -> fileService.schedulePathForDeletion(path, 0));
    }

    private boolean isProtectedFromPermanentDeletion(User user) {
        return AuthorizationCheckService.isAdmin(user.getAuthorities()) || user.isBot() || IRIS_BOT_LOGIN.equals(user.getLogin())
                || Objects.equals(internalAdminUsername, user.getLogin());
    }

    private UserDeletionResultDTO result(User user, UserDeletionResultStatus status, @Nullable String reason) {
        return new UserDeletionResultDTO(user.getId(), user.getLogin(), status, reason);
    }
}
