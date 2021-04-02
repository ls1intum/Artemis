package de.tum.in.www1.artemis.web.rest;

import java.net.*;
import java.util.List;

import javax.validation.Valid;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.*;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import io.github.jhipster.web.util.*;
import io.swagger.annotations.ApiParam;

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
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN')")
public class UserResource {

    private final Logger log = LoggerFactory.getLogger(UserResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final UserRepository userRepository;

    private final AuthorityRepository authorityRepository;

    public UserResource(UserRepository userRepository, UserService userService, UserCreationService userCreationService,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, AuthorityRepository authorityRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.authorityRepository = authorityRepository;
    }

    /**
     * POST /users : Creates a new user.
     * <p>
     * Creates a new user if the login and email are not already used, and sends an mail with an activation link. The user needs to be activated on creation.
     *
     * @param managedUserVM the user to create
     * @return the ResponseEntity with status 201 (Created) and with body the new user, or with status 400 (Bad Request) if the login or email is already in use
     * @throws URISyntaxException       if the Location URI syntax is incorrect
     * @throws BadRequestAlertException 400 (Bad Request) if the login or email is already in use
     */
    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<User> createUser(@Valid @RequestBody ManagedUserVM managedUserVM) throws URISyntaxException {
        log.debug("REST request to save User : {}", managedUserVM);

        if (managedUserVM.getId() != null) {
            throw new BadRequestAlertException("A new user cannot already have an ID", "userManagement", "idexists");
            // Lowercase the user login before comparing with database
        }
        else if (userRepository.findOneByLogin(managedUserVM.getLogin().toLowerCase()).isPresent()) {
            throw new LoginAlreadyUsedException();
        }
        else if (userRepository.findOneByEmailIgnoreCase(managedUserVM.getEmail()).isPresent()) {
            throw new EmailAlreadyUsedException();
        }
        else if (managedUserVM.getGroups().stream().anyMatch(group -> !artemisAuthenticationProvider.isGroupAvailable(group))) {
            throw new EntityNotFoundException("Not all groups are available: " + managedUserVM.getGroups());
        }
        else {
            User newUser = userCreationService.createUser(managedUserVM);

            // NOTE: Mail service is NOT active at the moment
            // mailService.sendCreationEmail(newUser);
            return ResponseEntity.created(new URI("/api/users/" + newUser.getLogin()))
                    .headers(HeaderUtil.createAlert(applicationName, "userManagement.created", newUser.getLogin())).body(newUser);
        }
    }

    /**
     * PUT /users : Updates an existing User.
     *
     * @param managedUserVM the user to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated user
     * @throws EmailAlreadyUsedException 400 (Bad Request) if the email is already in use
     * @throws LoginAlreadyUsedException 400 (Bad Request) if the login is already in use
     */
    @PutMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<UserDTO> updateUser(@Valid @RequestBody ManagedUserVM managedUserVM) {
        log.debug("REST request to update User : {}", managedUserVM);

        var existingUserByEmail = userRepository.findOneByEmailIgnoreCase(managedUserVM.getEmail());
        if (existingUserByEmail.isPresent() && (!existingUserByEmail.get().getId().equals(managedUserVM.getId()))) {
            throw new EmailAlreadyUsedException();
        }

        var existingUserByLogin = userRepository.findOneWithGroupsAndAuthoritiesByLogin(managedUserVM.getLogin().toLowerCase());
        if (existingUserByLogin.isPresent() && (!existingUserByLogin.get().getId().equals(managedUserVM.getId()))) {
            throw new LoginAlreadyUsedException();
        }

        if (managedUserVM.getGroups().stream().anyMatch(group -> !artemisAuthenticationProvider.isGroupAvailable(group))) {
            throw new EntityNotFoundException("Not all groups are available: " + managedUserVM.getGroups());
        }

        var existingUser = userRepository.findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(managedUserVM.getId());
        final var oldUserLogin = existingUser.getLogin();
        final var oldGroups = existingUser.getGroups();
        var updatedUser = userCreationService.updateInternalUser(existingUser, managedUserVM);
        userService.updateUserInConnectorsAndAuthProvider(existingUser, oldUserLogin, oldGroups);

        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "userManagement.updated", managedUserVM.getLogin())).body(new UserDTO(updatedUser));
    }

    /**
     * GET /users : get all users.
     *
     * @param userSearch the pagination information for user search
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers(@ApiParam PageableSearchDTO<String> userSearch) {
        final Page<UserDTO> page = userRepository.getAllManagedUsers(userSearch);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /users/search : search all users by login or name (result size is limited though on purpose, see below)
     *
     * @param loginOrName the login or name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("/users/search")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<UserDTO>> searchAllUsers(@RequestParam("loginOrName") String loginOrName) {
        log.debug("REST request to search all Users for {}", loginOrName);
        // restrict result size by only allowing reasonable searches
        if (loginOrName.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'loginOrName' must be three characters or longer.");
        }
        // limit search results to 25 users (larger result sizes would impact performance and are not useful for specific user searches)
        final Page<UserDTO> page = userRepository.searchAllUsersByLoginOrName(PageRequest.of(0, 25), loginOrName);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * @return a string list of the all of the roles
     */
    @GetMapping("/users/authorities")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public List<String> getAuthorities() {
        return authorityRepository.getAuthorities();
    }

    /**
     * GET /users/:login : get the "login" user.
     *
     * @param login the login of the user to find
     * @return the ResponseEntity with status 200 (OK) and with body the "login" user, or with status 404 (Not Found)
     */
    @GetMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<UserDTO> getUser(@PathVariable String login) {
        log.debug("REST request to get User : {}", login);
        return ResponseUtil.wrapOrNotFound(userRepository.findOneWithGroupsAndAuthoritiesByLogin(login).map(UserDTO::new));
    }

    /**
     * DELETE /users/:login : delete the "login" User.
     *
     * @param login the login of the user to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable String login) {
        log.debug("REST request to delete User: {}", login);
        userService.deleteUser(login);
        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "userManagement.deleted", login)).build();
    }

    @PutMapping("/users/notification-date")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> updateUserNotificationDate() {
        log.debug("REST request to update notification date for logged in user");
        User user = userRepository.getUser();
        userRepository.updateUserNotificationReadDate(user.getId());
        return ResponseEntity.ok().build();
    }
}
