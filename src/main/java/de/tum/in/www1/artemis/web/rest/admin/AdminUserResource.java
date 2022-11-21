package de.tum.in.www1.artemis.web.rest.admin;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.dto.UserPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EmailAlreadyUsedException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.errors.LoginAlreadyUsedException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import io.swagger.annotations.ApiParam;
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
@RestController
@RequestMapping("api/admin/")
public class AdminUserResource {

    private final Logger log = LoggerFactory.getLogger(AdminUserResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final UserRepository userRepository;

    private final AuthorityRepository authorityRepository;

    private final Optional<LdapUserService> ldapUserService;

    public AdminUserResource(UserRepository userRepository, UserService userService, UserCreationService userCreationService,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, AuthorityRepository authorityRepository, Optional<LdapUserService> ldapUserService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.authorityRepository = authorityRepository;
        this.ldapUserService = ldapUserService;
    }

    /**
     * POST users : Creates a new user.
     * <p>
     * Creates a new user if the login and email are not already used, and sends an email with an activation link. The user needs to be activated on creation.
     *
     * @param managedUserVM the user to create. If the password is null, a random one will be generated
     * @return the ResponseEntity with status 201 (Created) and with body the new user, or with status 400 (Bad Request) if the login or email is already in use
     * @throws URISyntaxException       if the Location URI syntax is incorrect
     * @throws BadRequestAlertException 400 (Bad Request) if the login or email is already in use
     */
    @PostMapping("users")
    @EnforceAdmin
    public ResponseEntity<User> createUser(@Valid @RequestBody ManagedUserVM managedUserVM) throws URISyntaxException {

        this.userService.checkUsernameAndPasswordValidityElseThrow(managedUserVM.getLogin(), managedUserVM.getPassword());

        log.debug("REST request to save User : {}", managedUserVM);

        if (managedUserVM.getId() != null) {
            throw new BadRequestAlertException("A new user cannot already have an ID", "userManagement", "idExists");
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
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.created", newUser.getLogin())).body(newUser);
        }
    }

    /**
     * PUT users : Updates an existing User.
     *
     * @param managedUserVM the user to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated user
     * @throws EmailAlreadyUsedException 400 (Bad Request) if the email is already in use
     * @throws LoginAlreadyUsedException 400 (Bad Request) if the login is already in use
     */
    @PutMapping("users")
    @EnforceAdmin
    public ResponseEntity<UserDTO> updateUser(@Valid @RequestBody ManagedUserVM managedUserVM) {
        this.userService.checkUsernameAndPasswordValidityElseThrow(managedUserVM.getLogin(), managedUserVM.getPassword());
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

        final boolean shouldActivateUser = !existingUser.getActivated() && managedUserVM.isActivated();
        final var oldUserLogin = existingUser.getLogin();
        final var oldGroups = existingUser.getGroups();
        var updatedUser = userCreationService.updateUser(existingUser, managedUserVM);
        userService.updateUserInConnectorsAndAuthProvider(updatedUser, oldUserLogin, oldGroups, managedUserVM.getPassword());

        if (shouldActivateUser) {
            userService.activateUser(updatedUser);
        }

        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.updated", managedUserVM.getLogin())).body(new UserDTO(updatedUser));
    }

    /**
     * PUT ldap : Updates an existing User based on the info available in the LDAP server.
     *
     * @param userId of the user to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated user
     */
    @PutMapping("users/{userId}/sync-ldap")
    @EnforceAdmin
    @Profile("ldap")
    public ResponseEntity<UserDTO> syncUserViaLdap(@PathVariable Long userId) {
        log.debug("REST request to update ldap information User : {}", userId);

        var user = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(userId);

        ldapUserService.ifPresent(service -> service.loadUserDetailsFromLdap(user));
        userCreationService.saveUser(user);

        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "userManagement.updated", userId.toString())).body(new UserDTO(user));
    }

    /**
     * GET users : get all users.
     *
     * @param userSearch the pagination information for user search
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("users")
    @EnforceAdmin
    public ResponseEntity<List<UserDTO>> getAllUsers(@ApiParam UserPageableSearchDTO userSearch) {
        final Page<UserDTO> page = userRepository.getAllManagedUsers(userSearch);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * @return a string list of the all the roles
     */
    @GetMapping("users/authorities")
    @EnforceAdmin
    public List<String> getAuthorities() {
        return authorityRepository.getAuthorities();
    }

    /**
     * DELETE users/:login : delete the "login" User.
     *
     * @param login the login of the user to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("users/{login:" + Constants.LOGIN_REGEX + "}")
    @EnforceAdmin
    public ResponseEntity<Void> deleteUser(@PathVariable String login) {
        log.debug("REST request to delete User: {}", login);
        if (userRepository.isCurrentUser(login)) {
            throw new BadRequestAlertException("You cannot delete yourself", "userManagement", "cannotDeleteYourself");
        }
        userService.deleteUser(login);
        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.deleted", login)).build();
    }

    /**
     * Delete users: deletes the provided users
     *
     * @param logins user logins to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("users")
    @EnforceAdmin
    public ResponseEntity<List<String>> deleteUsers(@RequestParam(name = "login") List<String> logins) {
        log.debug("REST request to delete {} users", logins.size());
        List<String> deletedUsers = new java.util.ArrayList<>();

        // Get current user and remove current user from list of logins
        var currentUser = userRepository.getUser();
        logins.remove(currentUser.getLogin());

        for (String login : logins) {
            try {
                if (!userRepository.isCurrentUser(login)) {
                    userService.deleteUser(login);
                    deletedUsers.add(login);
                }
            }
            catch (Exception exception) {
                // In order to handle all users even if some users produce exceptions, we catch them and ignore them and proceed with the remaining users
                log.error("REST request to delete user {} failed", login);
                log.error(exception.getMessage(), exception);
            }
        }
        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.batch.deleted", String.valueOf(deletedUsers.size())))
                .body(deletedUsers);
    }
}
