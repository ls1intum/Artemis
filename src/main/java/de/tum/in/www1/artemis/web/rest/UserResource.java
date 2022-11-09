package de.tum.in.www1.artemis.web.rest;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.LtiUserIdRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.dto.UserInitializationDTO;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.service.user.UserService;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

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
@RestController
@RequestMapping("api/")
public class UserResource {

    private final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final LtiUserIdRepository ltiUserIdRepository;

    public UserResource(UserRepository userRepository, UserService userService, UserCreationService userCreationService, LtiUserIdRepository ltiUserIdRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.ltiUserIdRepository = ltiUserIdRepository;
    }

    /**
     * GET users/search : search all users by login or name (result size is limited though on purpose, see below)
     *
     * @param loginOrName the login or name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("users/search")
    @PreAuthorize("hasRole('INSTRUCTOR')")
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
        });
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET users/:login : get the "login" user.
     *
     * @param login the login of the user to find
     * @return the ResponseEntity with status 200 (OK) and with body the "login" user, or with status 404 (Not Found)
     */
    @GetMapping("users/{login:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<UserDTO> getUser(@PathVariable String login) {
        log.debug("REST request to get User : {}", login);
        return ResponseUtil.wrapOrNotFound(userRepository.findOneWithGroupsAndAuthoritiesByLogin(login).map(user -> {
            user.setVisibleRegistrationNumber();
            return new UserDTO(user);
        }));
    }

    @PutMapping("users/notification-date")
    @PreAuthorize("hasRole('USER')")
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
    @PreAuthorize("hasRole('USER')")
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
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserInitializationDTO> initializeUser() {
        User user = userRepository.getUser();
        if (user.getActivated()) {
            return ResponseEntity.ok().body(new UserInitializationDTO());
        }
        if (ltiUserIdRepository.findByUser(user).isEmpty() || !user.isInternal()) {
            user.setActivated(true);
            userRepository.save(user);
            return ResponseEntity.ok().body(new UserInitializationDTO());
        }

        String result = userCreationService.setRandomPasswordAndReturn(user);
        return ResponseEntity.ok().body(new UserInitializationDTO(result));
    }
}
