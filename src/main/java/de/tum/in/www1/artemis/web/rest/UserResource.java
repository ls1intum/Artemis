package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.config.icl.ssh.HashUtils;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.connectors.lti.LtiService;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.dto.UserInitializationDTO;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing users.
 * <p>
 * This class accesses the {@link User} entity, and needs to fetch its collection of authorities.
 * <p>
 * For a normal use-case, it would be better to have an eager relationship between User and Authority, and send everything to the client side: there would be no View Model and DTO,
 * a lot less code, and an outer-join which would be good for performance.
 * <p>
 * We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will quite often do relationships with the user, and we don't want them to get the
 * authorities all the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users' application because of this use-case.</li>
 * <li>Not having an outer join causes n+1 requests to the database. This is not a real issue as we have by default a second-level cache. This means on the first HTTP call we do
 * the n+1 requests, but then all authorities come from the cache, so in fact it's much better than doing an outer join (which will get lots of data from the database, for each
 * HTTP call).</li>
 * <li>As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class UserResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final Optional<LtiService> ltiService;

    private final UserRepository userRepository;

    public UserResource(UserRepository userRepository, UserService userService, UserCreationService userCreationService, Optional<LtiService> ltiService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.ltiService = ltiService;
        this.userCreationService = userCreationService;
    }

    /**
     * GET users/search : search all users by login or name (result size is limited though on purpose, see below)
     *
     * @param loginOrName the login or name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("users/search")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<UserDTO>> searchAllUsers(@RequestParam("loginOrName") String loginOrName) {
        log.debug("REST request to search all Users for {}", loginOrName);
        // restrict result size by only allowing reasonable searches
        if (loginOrName.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'loginOrName' must be three characters or longer.");
        }
        // limit search results to 25 users (larger result sizes would impact performance and are not useful for specific user searches)
        final Page<UserDTO> page = userRepository.searchAllUsersByLoginOrName(PageRequest.of(0, 25), loginOrName);
        page.forEach(user -> {
            // remove some values which are not needed in the client
            user.setLangKey(null);
            user.setLastNotificationRead(null);
            user.setLastModifiedBy(null);
            user.setLastModifiedDate(null);
            user.setCreatedBy(null);
            user.setCreatedDate(null);
            user.setIrisAccepted(null);
        });
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @PutMapping("users/notification-date")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> updateUserNotificationDate() {
        log.debug("REST request to update notification date for logged-in user");
        User user = userRepository.getUser();
        userRepository.updateUserNotificationReadDate(user.getId());
        return ResponseEntity.ok().build();
    }

    /**
     * Updates the HideNotificationsUntil property that indicates which notifications to show (based on their creation date)
     *
     * @param showAllNotifications is true if all notifications should be displayed in the sidebar else depending on the HideNotificationsUntil property
     * @return void
     */
    @PutMapping("users/notification-visibility")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> updateUserNotificationVisibility(@RequestBody boolean showAllNotifications) {
        log.debug("REST request to update notification visibility for logged-in user");
        User user = userRepository.getUser();
        // if all notifications (regardless of their creation date) should be shown hideUntil should be null
        ZonedDateTime hideUntil = showAllNotifications ? null : ZonedDateTime.now();
        userService.updateUserNotificationVisibility(user.getId(), hideUntil);
        return ResponseEntity.ok().build();
    }

    /**
     * Initialises users that are flagged as such and are LTI users by setting a new password that gets returned
     *
     * @return The ResponseEntity with a status 200 (Ok) and either an empty password or the newly created password
     */
    @PutMapping("users/initialize")
    @EnforceAtLeastStudent
    public ResponseEntity<UserInitializationDTO> initializeUser() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (user.getActivated()) {
            return ResponseEntity.ok().body(new UserInitializationDTO(null));
        }
        if ((ltiService.isPresent() && !ltiService.get().isLtiCreatedUser(user)) || !user.isInternal()) {
            user.setActivated(true);
            userRepository.save(user);
            return ResponseEntity.ok().body(new UserInitializationDTO(null));
        }

        String result = userCreationService.setRandomPasswordAndReturn(user);
        return ResponseEntity.ok().body(new UserInitializationDTO(result));
    }

    /**
     * PUT users/accept-iris : sets the irisAccepted flag for the user to ZonedDateTime.now()
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request) if Iris was already accepted
     */
    @PutMapping("users/accept-iris")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> setIrisAcceptedToTimestamp() {
        User user = userRepository.getUser();
        if (user.getIrisAcceptedTimestamp() != null) {
            return ResponseEntity.badRequest().build();
        }
        userRepository.updateIrisAcceptedToDate(user.getId(), ZonedDateTime.now());
        return ResponseEntity.ok().build();
    }

    /**
     * PUT users/sshpublickey : sets the ssh public key
     *
     * @param sshPublicKey the ssh public key to set
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @PutMapping("users/sshpublickey")
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
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "sshUserSettings", "saveSshKeyError", "Invalid SSH key format"))
                    .body(null);
        }
        // Extract the PublicKey object
        PublicKey publicKey = keyEntry.resolvePublicKey(null, null, null);
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);
        userRepository.updateUserSshPublicKeyHash(user.getId(), keyHash, sshPublicKey);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT users/sshpublickey : sets the ssh public key
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @DeleteMapping("users/sshpublickey")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteSshPublicKey() {
        User user = userRepository.getUser();
        log.debug("REST request to remove SSH key of user {}", user.getLogin());
        userRepository.updateUserSshPublicKeyHash(user.getId(), null, null);

        log.debug("Successfully deleted SSH key of user {}", user.getLogin());
        return ResponseEntity.ok().build();
    }

    /**
     * GET users/vcsToken : get the vcsToken for of a user for a participation
     *
     * @param participationId the participation for which the access token should be fetched
     *
     * @return the versionControlAccessToken belonging to the provided participation and user
     */
    @GetMapping("users/vcsToken")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getVcsAccessToken(@RequestParam("participationId") Long participationId) {
        User user = userRepository.getUser();

        log.debug("REST request to get VCS access token of user {} for participation {}", user.getLogin(), participationId);
        return ResponseEntity.ok(userService.getVcsAccessTokenForUserElseThrow(user, participationId).getVcsAccessToken());
    }

    /**
     * PUT users/vcsToken : get the vcsToken for of a user for a participation
     *
     * @param participationId the participation for which the access token should be fetched
     *
     * @return the versionControlAccessToken belonging to the provided participation and user
     */
    @PutMapping("users/vcsToken")
    @EnforceAtLeastStudent
    public ResponseEntity<String> createVcsAccessToken(@RequestParam("participationId") Long participationId) {
        User user = userRepository.getUser();

        log.debug("REST request to create a new VCS access token for user {} for participation {}", user.getLogin(), participationId);
        return ResponseEntity.ok(userService.createNewVcsAccessTokenForUser(user, participationId).getVcsAccessToken());
    }
}
