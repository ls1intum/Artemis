package de.tum.cit.aet.artemis.account.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.Path;
import java.time.ZonedDateTime;

import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.BasicInformationUpdateResultDTO;
import de.tum.cit.aet.artemis.account.dto.PendingEmailChangeDTO;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.AccountCredentialRevocationService;
import de.tum.cit.aet.artemis.account.service.AccountSecurityNotificationService;
import de.tum.cit.aet.artemis.account.service.AccountService;
import de.tum.cit.aet.artemis.account.service.EmailChangeService;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.dto.PasswordChangeDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EmailAlreadyUsedException;
import de.tum.cit.aet.artemis.core.exception.PasswordViolatesRequirementsException;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.SessionFreshnessService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.localvc.service.LocalVCPersonalAccessTokenManagementService;
import de.tum.cit.aet.artemis.localvc.service.UserVcsAccessTokenService;

/**
 * REST controller for managing the current user's account.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.ACCOUNT_SETTINGS)
@RestController
@RequestMapping("api/account/")
public class AccountResource {

    public static final String ENTITY_NAME = "user";

    private static final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserVcsAccessTokenService userVcsAccessTokenService;

    private final UserAiPreferenceService userAiPreferenceService;

    private final UserService userService;

    private final AccountService accountService;

    private final FileService fileService;

    private final AccountCredentialRevocationService accountCredentialRevocationService;

    private final AccountSecurityNotificationService accountSecurityNotificationService;

    private final SessionFreshnessService sessionFreshnessService;

    private final EmailChangeService emailChangeService;

    private static final float MAX_PROFILE_PICTURE_FILESIZE_IN_MEGABYTES = 0.1f;

    public AccountResource(UserRepository userRepository, UserService userService, AccountService accountService, FileService fileService,
            AccountCredentialRevocationService accountCredentialRevocationService, AccountSecurityNotificationService accountSecurityNotificationService,
            UserVcsAccessTokenService userVcsAccessTokenService, UserAiPreferenceService userAiPreferenceService, SessionFreshnessService sessionFreshnessService,
            EmailChangeService emailChangeService) {
        this.userRepository = userRepository;
        this.userAiPreferenceService = userAiPreferenceService;
        this.userVcsAccessTokenService = userVcsAccessTokenService;
        this.userService = userService;
        this.accountService = accountService;
        this.fileService = fileService;
        this.accountCredentialRevocationService = accountCredentialRevocationService;
        this.accountSecurityNotificationService = accountSecurityNotificationService;
        this.sessionFreshnessService = sessionFreshnessService;
        this.emailChangeService = emailChangeService;
    }

    /**
     * PUT basic-information : update the basic information (name, email, language, image) of the current user's account.
     * <p>
     * A new e-mail address of an internal account is only requested here: it is applied once the owners of both the current and the new address have confirmed it by mail. The
     * other fields are saved immediately.
     *
     * @param userDTO the current user information.
     * @return the ResponseEntity with status 200 (OK) when the user information is updated, with the e-mail change that is waiting for confirmation, if there is one.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws RuntimeException          {@code 500 (Internal Server Error)} if the user login wasn't found.
     */
    @PutMapping("basic-information")
    @EnforceAtLeastStudent
    public ResponseEntity<BasicInformationUpdateResultDTO> saveAccount(@Valid @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(accountService.updateBasicInformationOfCurrentUser(userDTO));
    }

    /**
     * GET pending-email-change : the e-mail change of the current user that is waiting for confirmation.
     *
     * @return the ResponseEntity with status 200 (OK) and the pending change, or 204 (No Content) if none is pending
     */
    @GetMapping("pending-email-change")
    @EnforceAtLeastStudent
    public ResponseEntity<PendingEmailChangeDTO> getPendingEmailChange() {
        return emailChangeService.findPendingChange(userRepository.getUserIdElseThrow()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * DELETE pending-email-change : withdraws the e-mail change of the current user that is waiting for confirmation.
     *
     * @return the ResponseEntity with status 200 (OK), also if nothing was pending
     */
    @DeleteMapping("pending-email-change")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> cancelPendingEmailChange() {
        emailChangeService.cancel(userRepository.getUserIdElseThrow());
        return ResponseEntity.ok().build();
    }

    /**
     * POST pending-email-change/resend : mails new confirmation links for the pending e-mail change to the current and the new address.
     *
     * @return the ResponseEntity with status 200 (OK) and the renewed pending change
     * @throws BadRequestAlertException {@code 400 (Bad Request)} if no change is pending, or the last mails went out a moment ago
     */
    @PostMapping("pending-email-change/resend")
    @EnforceAtLeastStudent
    public ResponseEntity<PendingEmailChangeDTO> resendPendingEmailChange() {
        return ResponseEntity.ok(emailChangeService.resend(userRepository.getUser()));
    }

    /**
     * {@code POST /account/change-password} : changes the current user's password.
     *
     * @param passwordChangeDto current and new password.
     * @return the ResponseEntity with status 200 (OK) when the password has been changed.
     * @throws PasswordViolatesRequirementsException {@code 400 (Bad Request)} if the new password does not meet the requirements.
     */
    @FeatureUsage(UserFeature.REGISTRATION_PASSWORD)
    @PostMapping("change-password")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> changePassword(@RequestBody PasswordChangeDTO passwordChangeDto) {
        User user = userRepository.getUser();
        if (!user.isInternal()) {
            throw new AccessForbiddenException("Only users with internally saved credentials can change their password.");
        }
        if (accountService.isPasswordLengthInvalid(passwordChangeDto.newPassword())) {
            throw new PasswordViolatesRequirementsException();
        }
        userService.changePassword(passwordChangeDto.currentPassword(), passwordChangeDto.newPassword(), passwordChangeDto.revokeCredentialsOrNone());

        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST /account/revoke-credentials} : revokes the selected credentials of the current user, without changing their password.
     * <p>
     * Deliberately available to external users as well. They cannot change their password here at all -- it lives in their identity provider -- so tying revocation to a
     * password change left them with no way to withdraw a passkey, SSH key or access token in one step; they had to delete each one individually across three settings
     * pages. The credentials being revoked are stored by Artemis either way, so who owns the password does not change what can be revoked.
     * <p>
     * Guarded like the per-credential delete endpoints it replaces in bulk ({@code DELETE passkey/{id}}, {@code DELETE user-vcs-access-token},
     * {@code DELETE public-keys/{id}}), which require a session and nothing more. This endpoint therefore grants nobody a capability they did not already have; it only
     * saves the owner from doing it one credential at a time.
     *
     * @param choice which credential types to revoke
     * @return the ResponseEntity with status 200 (OK) when the selected credentials have been revoked
     * @throws BadRequestAlertException {@code 400 (Bad Request)} if the request selects no credential type
     */
    @PostMapping("revoke-credentials")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> revokeCredentials(@RequestBody CredentialRevocationChoiceDTO choice) {
        if (!choice.revokesAnything()) {
            throw new BadRequestAlertException("At least one credential type must be selected for revocation", ENTITY_NAME, "noCredentialTypeSelected");
        }
        User user = userRepository.getUser();
        log.info("REST request to revoke the credentials of user {}: passkeys={}, sshKeys={}, vcsAccessTokens={}", user.getLogin(), choice.passkeys(), choice.sshKeys(),
                choice.vcsAccessTokens());
        accountCredentialRevocationService.revokeSelectedCredentials(user, choice, "revoked by the user");
        accountSecurityNotificationService.credentialsRevoked(user, choice);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT user-vcs-access-token : creates a vcsAccessToken for a user
     *
     * @param expiryDate The expiry date which should be set for the token
     * @return the ResponseEntity with a userDTO containing the token: with status 200 (OK), with status 404 (Not Found), with status 400 (Bad Request), or with status 401
     *         (Unauthorized) if the session was established before the credentials of the account last changed
     */
    @FeatureUsage(UserFeature.GIT_CREDENTIALS)
    @PutMapping("user-vcs-access-token")
    @EnforceAtLeastStudent
    public ResponseEntity<UserDTO> createVcsAccessToken(@RequestParam("expiryDate") ZonedDateTime expiryDate) {
        sessionFreshnessService.requireCurrentSession();
        User user = userRepository.getUser();
        log.debug("REST request to create a new VCS access token for user {}", user.getLogin());
        if (expiryDate.isBefore(ZonedDateTime.now()) || expiryDate.isAfter(ZonedDateTime.now().plusYears(1))) {
            throw new BadRequestException("Invalid expiry date provided");
        }

        String token = LocalVCPersonalAccessTokenManagementService.generateSecureVCSAccessToken();
        userVcsAccessTokenService.store(user.getId(), token, expiryDate);
        log.debug("Successfully created a VCS access token for user {}", user.getLogin());
        UserDTO userDTO = new UserDTO();
        userDTO.setLogin(user.getLogin());
        // Returned from what was just generated rather than read back: the plaintext exists only here.
        userDTO.setVcsAccessToken(token);
        userDTO.setVcsAccessTokenExpiryDate(expiryDate);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * DELETE user-vcs-access-token : deletes the vcsAccessToken of a user
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @FeatureUsage(UserFeature.GIT_CREDENTIALS)
    @DeleteMapping("user-vcs-access-token")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteVcsAccessToken() {
        User user = userRepository.getUser();
        log.debug("REST request to remove VCS access token key of user {}", user.getLogin());
        userVcsAccessTokenService.revoke(user.getId());
        log.debug("Successfully deleted VCS access token of user {}", user.getLogin());
        return ResponseEntity.ok().build();
    }

    /**
     * GET participation-vcs-access-token : get the vcsToken for of a user for a participation
     *
     * @param participationId the participation for which the access token should be fetched
     *
     * @return the versionControlAccessToken belonging to the provided participation and user, or status 401 (Unauthorized) if the session was established before the
     *         credentials of the account last changed
     */
    @FeatureUsage(UserFeature.GIT_CREDENTIALS)
    @GetMapping("participation-vcs-access-token")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<String> getVcsAccessToken(@RequestParam("participationId") Long participationId) {
        // The token is minted when a participation is started, so reading it back is how a session gets a credential that outlives it.
        sessionFreshnessService.requireCurrentSession();
        User user = userRepository.getUser();

        log.debug("REST request to get VCS access token of user {} for participation {}", user.getLogin(), participationId);
        return ResponseEntity.ok(userService.getParticipationVcsAccessTokenForUserAndParticipationIdOrElseThrow(user, participationId).getVcsAccessToken());
    }

    /**
     * PUT participation-vcs-access-token : add a vcsToken for of a user for a participation
     *
     * @param participationId the participation for which the access token should be fetched
     *
     * @return the versionControlAccessToken belonging to the provided participation and user, or status 401 (Unauthorized) if the session was established before the
     *         credentials of the account last changed
     */
    @FeatureUsage(UserFeature.GIT_CREDENTIALS)
    @PutMapping("participation-vcs-access-token")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<String> createVcsAccessToken(@RequestParam("participationId") Long participationId) {
        sessionFreshnessService.requireCurrentSession();
        User user = userRepository.getUser();

        log.debug("REST request to create a new VCS access token for user {} for participation {}", user.getLogin(), participationId);
        return ResponseEntity.ok(userService.createParticipationVcsAccessTokenForUserAndParticipationIdOrElseThrow(user, participationId).getVcsAccessToken());
    }

    /**
     * PUT profile-picture : upload a profile picture
     *
     * @param file the image file that is being uploaded
     * @return the ResponseEntity with status 200 (OK) and with body of current user
     */
    @PutMapping("profile-picture")
    @EnforceAtLeastStudent
    public ResponseEntity<UserDTO> updateProfilePicture(@RequestPart MultipartFile file) {
        log.debug("REST request to update profile picture for logged-in user");
        String contentType = file.getContentType();

        // The declared content type comes from the client and only filters out obvious mistakes; saveImageFile checks the content itself
        if (contentType == null || (!contentType.equals("image/png") && !contentType.equals("image/jpeg") && !contentType.equals("image/jpg"))) {
            throw new BadRequestAlertException("The file format is not supported, please make sure to upload a .png or .jpg file.", ENTITY_NAME,
                    "profilePictureFileFormatNotSupported", true);
        }
        else if (file.getSize() > Math.floor(MAX_PROFILE_PICTURE_FILESIZE_IN_MEGABYTES * 1024 * 1024)) {
            throw new BadRequestAlertException("The filesize of your image is too big, please upload a smaller one.", ENTITY_NAME, "profilePictureFilesizeTooBig", true);
        }

        User user = userRepository.getUser();
        Path basePath = FilePathConverter.getProfilePictureFilePath();

        Path savePath = FileUtil.saveImageFile(file, basePath, FilePathType.PROFILE_PICTURE);

        // Delete the previous picture only once the new one is stored, so a rejected upload keeps it
        if (user.getImageUrl() != null) {
            fileService.schedulePathForDeletion(new FileSystemLocation.ProfilePicture(user.getImageUrl()).path(), 0);
        }

        // The column stores the filename; the URL the client needs is assembled on the way out, see User.getImageUrl().
        String filename = savePath.getFileName().toString();
        userRepository.updateUserImageUrl(user.getId(), filename);
        user.setImageUrl(filename);
        return ResponseEntity.ok(new UserDTO(user));
    }

    /**
     * DELETE profile-picture : remove current users profile picture
     *
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("profile-picture")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> removeProfilePicture() {
        log.debug("REST request to remove profile picture for logged-in user");
        User user = userRepository.getUser();
        if (user.getImageUrl() != null) {
            fileService.schedulePathForDeletion(new FileSystemLocation.ProfilePicture(user.getImageUrl()).path(), 0);
            userRepository.updateUserImageUrl(user.getId(), null);
        }
        return ResponseEntity.ok().build();
    }
}
