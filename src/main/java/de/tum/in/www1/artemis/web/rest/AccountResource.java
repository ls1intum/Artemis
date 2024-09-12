package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

import de.tum.in.www1.artemis.config.icl.ssh.HashUtils;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AccountService;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCPersonalAccessTokenManagementService;
import de.tum.in.www1.artemis.service.dto.PasswordChangeDTO;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EmailAlreadyUsedException;
import de.tum.in.www1.artemis.web.rest.errors.PasswordViolatesRequirementsException;

/**
 * REST controller for managing the current user's account.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class AccountResource {

    public static final String ENTITY_NAME = "user";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final AccountService accountService;

    private final FileService fileService;

    private static final float MAX_PROFILE_PICTURE_FILESIZE_IN_MEGABYTES = 0.1f;

    public AccountResource(UserRepository userRepository, UserService userService, UserCreationService userCreationService, AccountService accountService,
            FileService fileService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.accountService = accountService;
        this.fileService = fileService;
    }

    /**
     * PUT /account : update the provided account.
     *
     * @param userDTO the current user information.
     * @return the ResponseEntity with status 200 (OK) when the user information is updated.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws RuntimeException          {@code 500 (Internal Server Error)} if the user login wasn't found.
     */
    @PutMapping("account")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> saveAccount(@Valid @RequestBody UserDTO userDTO) {
        if (accountService.isRegistrationDisabled()) {
            throw new AccessForbiddenException("Can't edit user information as user registration is disabled");
        }

        final String userLogin = userRepository.getUser().getLogin();
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getLogin().equalsIgnoreCase(userLogin))) {
            throw new EmailAlreadyUsedException();
        }

        userCreationService.updateBasicInformationOfCurrentUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(), userDTO.getLangKey(), userDTO.getImageUrl());

        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST /account/change-password} : changes the current user's password.
     *
     * @param passwordChangeDto current and new password.
     * @return the ResponseEntity with status 200 (OK) when the password has been changed.
     * @throws PasswordViolatesRequirementsException {@code 400 (Bad Request)} if the new password does not meet the requirements.
     */
    @PostMapping("account/change-password")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> changePassword(@RequestBody PasswordChangeDTO passwordChangeDto) {
        User user = userRepository.getUser();
        if (!user.isInternal()) {
            throw new AccessForbiddenException("Only users with internally saved credentials can change their password.");
        }
        if (accountService.isPasswordLengthInvalid(passwordChangeDto.newPassword())) {
            throw new PasswordViolatesRequirementsException();
        }
        userService.changePassword(passwordChangeDto.currentPassword(), passwordChangeDto.newPassword());

        return ResponseEntity.ok().build();
    }

    /**
     * PUT account/ssh-public-key : sets the ssh public key
     *
     * @param sshPublicKey the ssh public key to set
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @PutMapping("account/ssh-public-key")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> addSshPublicKey(@RequestBody String sshPublicKey) throws GeneralSecurityException, IOException {

        User user = userRepository.getUser();
        log.debug("REST request to add SSH key to user {}", user.getLogin());
        // Parse the public key string
        AuthorizedKeyEntry keyEntry;
        try {
            keyEntry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(sshPublicKey);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("Invalid SSH key format", "SSH key", "invalidKeyFormat", true);
        }
        // Extract the PublicKey object
        PublicKey publicKey = keyEntry.resolvePublicKey(null, null, null);
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);
        userRepository.updateUserSshPublicKeyHash(user.getId(), keyHash, sshPublicKey);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT account/ssh-public-key : sets the ssh public key
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @DeleteMapping("account/ssh-public-key")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteSshPublicKey() {
        User user = userRepository.getUser();
        log.debug("REST request to remove SSH key of user {}", user.getLogin());
        userRepository.updateUserSshPublicKeyHash(user.getId(), null, null);

        log.debug("Successfully deleted SSH key of user {}", user.getLogin());
        return ResponseEntity.ok().build();
    }

    /**
     * PUT account/user-vcs-access-token : creates a vcsAccessToken for a user
     *
     * @param expiryDate The expiry date which should be set for the token
     * @return the ResponseEntity with a userDTO containing the token: with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @PutMapping("account/user-vcs-access-token")
    @EnforceAtLeastStudent
    public ResponseEntity<UserDTO> createVcsAccessToken(@RequestParam("expiryDate") ZonedDateTime expiryDate) {
        User user = userRepository.getUser();
        log.debug("REST request to create a new VCS access token for user {}", user.getLogin());
        if (expiryDate.isBefore(ZonedDateTime.now()) || expiryDate.isAfter(ZonedDateTime.now().plusYears(1))) {
            throw new BadRequestException("Invalid expiry date provided");
        }

        userRepository.updateUserVcsAccessToken(user.getId(), LocalVCPersonalAccessTokenManagementService.generateSecureVCSAccessToken(), expiryDate);
        log.debug("Successfully created a VCS access token for user {}", user.getLogin());
        user = userRepository.getUser();
        UserDTO userDTO = new UserDTO();
        userDTO.setLogin(user.getLogin());
        userDTO.setVcsAccessToken(user.getVcsAccessToken());
        userDTO.setVcsAccessTokenExpiryDate(user.getVcsAccessTokenExpiryDate());
        return ResponseEntity.ok(userDTO);
    }

    /**
     * DELETE account/user-vcs-access-token : deletes the vcsAccessToken of a user
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @DeleteMapping("account/user-vcs-access-token")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteVcsAccessToken() {
        User user = userRepository.getUser();
        log.debug("REST request to remove VCS access token key of user {}", user.getLogin());
        userRepository.updateUserVcsAccessToken(user.getId(), null, null);
        log.debug("Successfully deleted VCS access token of user {}", user.getLogin());
        return ResponseEntity.ok().build();
    }

    /**
     * GET account/participation-vcs-access-token : get the vcsToken for of a user for a participation
     *
     * @param participationId the participation for which the access token should be fetched
     *
     * @return the versionControlAccessToken belonging to the provided participation and user
     */
    @GetMapping("account/participation-vcs-access-token")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getVcsAccessToken(@RequestParam("participationId") Long participationId) {
        User user = userRepository.getUser();

        log.debug("REST request to get VCS access token of user {} for participation {}", user.getLogin(), participationId);
        return ResponseEntity.ok(userService.getParticipationVcsAccessTokenForUserAndParticipationIdOrElseThrow(user, participationId).getVcsAccessToken());
    }

    /**
     * PUT account/participation-vcs-access-token : get the vcsToken for of a user for a participation
     *
     * @param participationId the participation for which the access token should be fetched
     *
     * @return the versionControlAccessToken belonging to the provided participation and user
     */
    @PutMapping("account/participation-vcs-access-token")
    @EnforceAtLeastStudent
    public ResponseEntity<String> createVcsAccessToken(@RequestParam("participationId") Long participationId) {
        User user = userRepository.getUser();

        log.debug("REST request to create a new VCS access token for user {} for participation {}", user.getLogin(), participationId);
        return ResponseEntity.ok(userService.createParticipationVcsAccessTokenForUserAndParticipationIdOrElseThrow(user, participationId).getVcsAccessToken());
    }

    /**
     * PUT account/profile-picture : upload a profile picture
     *
     * @param file the image file that is being uploaded
     * @return the ResponseEntity with status 200 (OK) and with body of current user
     */
    @PutMapping("account/profile-picture")
    @EnforceAtLeastStudent
    public ResponseEntity<UserDTO> updateProfilePicture(@RequestPart MultipartFile file) throws URISyntaxException {
        log.debug("REST request to update profile picture for logged-in user");
        String contentType = file.getContentType();

        // Check if the content type is either image/png or image/jpeg, else return 400
        if (contentType == null || (!contentType.equals("image/png") && !contentType.equals("image/jpeg") && !contentType.equals("image/jpg"))) {
            throw new BadRequestAlertException("The file format is not supported, please make sure to upload a .png or .jpg file.", ENTITY_NAME,
                    "profilePictureFileFormatNotSupported", true);
        }
        else if (file.getSize() > Math.floor(MAX_PROFILE_PICTURE_FILESIZE_IN_MEGABYTES * 1024 * 1024)) {
            throw new BadRequestAlertException("The filesize of your image is too big, please upload a smaller one.", ENTITY_NAME, "profilePictureFilesizeTooBig", true);
        }

        User user = userRepository.getUser();
        Path basePath = FilePathService.getProfilePictureFilePath();

        // Delete existing
        if (user.getImageUrl() != null) {
            fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(new URI(user.getImageUrl())), 0);
        }

        Path savePath = fileService.saveFile(file, basePath, false);
        String publicPath = FilePathService.publicPathForActualPathOrThrow(savePath, user.getId()).toString();
        userRepository.updateUserImageUrl(user.getId(), publicPath);
        user.setImageUrl(publicPath);
        return ResponseEntity.ok(new UserDTO(user));
    }

    /**
     * DELETE account/profile-picture : remove current users profile picture
     *
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("account/profile-picture")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> removeProfilePicture() throws URISyntaxException {
        log.debug("REST request to remove profile picture for logged-in user");
        User user = userRepository.getUser();
        if (user.getImageUrl() != null) {
            fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(new URI(user.getImageUrl())), 0);
            userRepository.updateUserImageUrl(user.getId(), null);
        }
        return ResponseEntity.ok().build();
    }
}
