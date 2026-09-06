package de.tum.cit.aet.artemis.account.web.admin;

import static de.tum.cit.aet.artemis.account.domain.User.IRIS_BOT_LOGIN;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.SUPER_ADMIN;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.account.config.AccountLegacyRestPaths;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.BulkUserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.BulkUserDeletionImpactRequestDTO;
import de.tum.cit.aet.artemis.account.dto.BulkUserDeletionRequestDTO;
import de.tum.cit.aet.artemis.account.dto.PermanentUserDeletionRequestDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultStatus;
import de.tum.cit.aet.artemis.account.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.account.service.user.deletion.PermanentUserDeletionService;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionMode;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionPlanService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.UserPageableSearchDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EmailAlreadyUsedException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.LoginAlreadyUsedException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.web.util.PaginationUtil;
import de.tum.cit.aet.artemis.core.web.util.ResponseUtil;

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
 * <li>Not having an outer join causes n+1 requests to the database. The {@code authorities} association uses {@code @BatchSize(20)} so the lookups are batched within a single
 * transaction, but each HTTP call pays the database round-trip. If this becomes a measured bottleneck, consider an explicit {@code @EntityGraph} or fetch join on the auth
 * path.</li>
 * <li>As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage("users/user-administration")
@RestController
@SuppressWarnings("deprecation")
@RequestMapping({ "api/account/admin/", AccountLegacyRestPaths.CORE_ADMIN_PREFIX })
public class AdminUserResource {

    private static final Logger log = LoggerFactory.getLogger(AdminUserResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final String artemisInternalAdminUsername;

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final AuthorityRepository authorityRepository;

    private final Optional<LdapUserService> ldapUserService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserDeletionPlanService userDeletionPlanService;

    private final PermanentUserDeletionService permanentUserDeletionService;

    public AdminUserResource(UserRepository userRepository, UserService userService, UserCreationService userCreationService, AuthorityRepository authorityRepository,
            Optional<LdapUserService> ldapUserService, AuthorizationCheckService authorizationCheckService, UserDeletionPlanService userDeletionPlanService,
            PermanentUserDeletionService permanentUserDeletionService,
            @Nullable @Value("${artemis.user-management.internal-admin.username:#{null}}") String artemisInternalAdminUsername) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.authorityRepository = authorityRepository;
        this.ldapUserService = ldapUserService;
        this.authorizationCheckService = authorizationCheckService;
        this.userDeletionPlanService = userDeletionPlanService;
        this.permanentUserDeletionService = permanentUserDeletionService;
        this.artemisInternalAdminUsername = artemisInternalAdminUsername;
    }

    /**
     * POST users : Creates a new user.
     * <p>
     * Creates a new user if the login and email are not already used, and sends an email with an activation link. The user needs to be activated on creation.
     *
     * @param userToBeCreated the user to create. If the password is null, a random one will be generated
     * @return the ResponseEntity with status 201 (Created) and with body the new user, or with status 400 (Bad Request) if the login or email is already in use
     * @throws URISyntaxException       if the Location URI syntax is incorrect
     * @throws BadRequestAlertException 400 (Bad Request) if the login or email is already in use
     */
    @PostMapping("users")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody ManagedUserVM userToBeCreated) throws URISyntaxException, AccessForbiddenAlertException {
        this.userService.checkUsernameAndPasswordValidityElseThrow(userToBeCreated.getLogin(), userToBeCreated.getPassword());

        log.debug("REST request to save User : {}", userToBeCreated);

        checkSuperAdminAuthorizationToManageAdmin(AuthorizationCheckService.isAdminByAuthorityName(userToBeCreated.getAuthorities()));

        if (userToBeCreated.getId() != null) {
            throw new BadRequestAlertException("A new user cannot already have an ID", "userManagement", "idExists");
            // Lowercase the user login before comparing with database
        }
        else if (IRIS_BOT_LOGIN.equalsIgnoreCase(userToBeCreated.getLogin())) {
            throw new BadRequestAlertException("The login '" + IRIS_BOT_LOGIN + "' is reserved and cannot be used.", "userManagement", "loginReserved");
        }
        else if (userRepository.findOneByLogin(userToBeCreated.getLogin().toLowerCase(Locale.ENGLISH)).isPresent()) {
            throw new LoginAlreadyUsedException();
        }
        else {
            User newUser = userCreationService.createUser(userToBeCreated);

            // NOTE: Mail service is NOT active at the moment
            // mailService.sendCreationEmail(newUser);
            return ResponseEntity.created(new URI("/api/account/admin/users/" + newUser.getLogin()))
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.created", newUser.getLogin())).body(new UserDTO(newUser));
        }
    }

    /**
     * PATCH users/:userId/activate : activate the user with the given login.
     *
     * @param userId the id of the user to activate
     * @return the ResponseEntity with status 200 (OK) and with body the activated user, or with status 404 (Not Found)
     */
    @PatchMapping("users/{userId}/activate")
    public ResponseEntity<UserDTO> activateUser(@PathVariable long userId) throws AccessForbiddenAlertException {
        log.debug("REST request to activate User {}", userId);
        return userRepository.findOneWithCourseRolesAndAuthoritiesById(userId).map(userToBeActivated -> {
            if (IRIS_BOT_LOGIN.equals(userToBeActivated.getLogin())) {
                throw new BadRequestAlertException("The Iris bot user cannot be modified via the API.", "userManagement", "cannotModifyIrisBot");
            }
            checkSuperAdminAuthorizationToManageAdmin(AuthorizationCheckService.isAdmin(userToBeActivated.getAuthorities()));
            userCreationService.activateUser(userToBeActivated);
            return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.activated", userToBeActivated.getLogin()))
                    .body(new UserDTO(userToBeActivated));
        }).orElseThrow(() -> new EntityNotFoundException("User", userId));
    }

    /**
     * PATCH users/:userId/deactivate : deactivate the user with the given login.
     *
     * @param userId the id of the user to deactivate
     * @return the ResponseEntity with status 200 (OK) and with body the deactivated user, or with status 404 (Not Found)
     */
    @PatchMapping("users/{userId}/deactivate")
    public ResponseEntity<UserDTO> deactivateUser(@PathVariable long userId) throws AccessForbiddenAlertException {
        log.debug("REST request to deactivate User {}", userId);
        return userRepository.findOneWithCourseRolesAndAuthoritiesById(userId).map(userToBeDeactivated -> {
            if (IRIS_BOT_LOGIN.equals(userToBeDeactivated.getLogin())) {
                throw new BadRequestAlertException("The Iris bot user cannot be modified via the API.", "userManagement", "cannotModifyIrisBot");
            }
            checkSuperAdminAuthorizationToManageAdmin(AuthorizationCheckService.isAdmin(userToBeDeactivated.getAuthorities()));
            userCreationService.deactivateUser(userToBeDeactivated);
            return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.deactivated", userToBeDeactivated.getLogin()))
                    .body(new UserDTO(userToBeDeactivated));
        }).orElseThrow(() -> new EntityNotFoundException("User", userId));
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
    public ResponseEntity<UserDTO> updateUser(@Valid @RequestBody ManagedUserVM managedUserVM) throws AccessForbiddenAlertException {
        this.userService.checkUsernameAndPasswordValidityElseThrow(managedUserVM.getLogin(), managedUserVM.getPassword());
        log.debug("REST request to update User : {}", managedUserVM);

        if (IRIS_BOT_LOGIN.equalsIgnoreCase(managedUserVM.getLogin())) {
            throw new BadRequestAlertException("The login '" + IRIS_BOT_LOGIN + "' is reserved and cannot be used.", "userManagement", "loginReserved");
        }

        var existingUserByLogin = userRepository.findOneByLogin(managedUserVM.getLogin().toLowerCase(Locale.ENGLISH));
        if (existingUserByLogin.isPresent() && (!existingUserByLogin.get().getId().equals(managedUserVM.getId()))) {
            throw new LoginAlreadyUsedException();
        }

        var existingUser = userRepository.findByIdWithCourseRolesAndAuthoritiesAndOrganizationsElseThrow(managedUserVM.getId());
        if (IRIS_BOT_LOGIN.equals(existingUser.getLogin())) {
            throw new BadRequestAlertException("The Iris bot user cannot be modified via the API.", "userManagement", "cannotModifyIrisBot");
        }
        boolean editedUserIsAdmin = AuthorizationCheckService.isAdmin(existingUser.getAuthorities());
        boolean requestedAdminEscalation = managedUserVM.getAuthorities() != null && AuthorizationCheckService.isAdminByAuthorityName(managedUserVM.getAuthorities());
        checkSuperAdminAuthorizationToManageAdmin(editedUserIsAdmin || requestedAdminEscalation);
        checkCannotRemoveSuperAdminFromDefaultAdmin(existingUser.getLogin(), managedUserVM.getAuthorities());

        final boolean shouldActivateUser = !existingUser.getActivated() && managedUserVM.isActivated();
        var updatedUser = userCreationService.updateUser(existingUser, managedUserVM);

        if (shouldActivateUser) {
            userService.activateUser(updatedUser);
        }

        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.updated", managedUserVM.getLogin())).body(new UserDTO(updatedUser));
    }

    /**
     * Checks if the current user has permission to manage admin users. Throws an exception if the operation involves
     * an admin user (either creating, editing, or deleting) and the current user is not a super admin.
     *
     * @param involvesAdminUser whether the operation involves managing an admin user
     * @throws AccessForbiddenAlertException if a non-super-admin tries to manage an admin user
     */
    private void checkSuperAdminAuthorizationToManageAdmin(boolean involvesAdminUser) {
        if (involvesAdminUser && !this.authorizationCheckService.isSuperAdmin()) {
            throw new AccessForbiddenAlertException("Only super administrators are allowed to manage administrators.", "userManagement",
                    "userManagement.onlySuperAdminCanManageAdmins");
        }
    }

    /**
     * Checks if the operation attempts to remove super admin rights from the default admin user defined in the configuration.
     * The default admin must always retain super admin rights to ensure system accessibility.
     *
     * @param login          the login of the user being modified
     * @param newAuthorities the new authorities to be assigned to the user (may be null if not changing authorities)
     * @throws BadRequestAlertException if attempting to remove super admin rights from the default admin
     */
    private void checkCannotRemoveSuperAdminFromDefaultAdmin(String login, Set<String> newAuthorities) {
        if (artemisInternalAdminUsername == null || newAuthorities == null) {
            return;
        }

        boolean isDefaultAdmin = artemisInternalAdminUsername.equals(login);
        boolean newAuthoritiesContainSuperAdmin = newAuthorities.contains(SUPER_ADMIN.getAuthority());

        if (isDefaultAdmin && !newAuthoritiesContainSuperAdmin) {
            throw new BadRequestAlertException("You cannot remove super admin rights from the default admin user.", "userManagement",
                    "userManagement.cannotRemoveDefaultAdminRights");
        }
    }

    /**
     * GET users/:login : get the "login" user.
     *
     * @param login the login of the user to find
     * @return the ResponseEntity with status 200 (OK) and with body the "login" user, or with status 404 (Not Found)
     */
    @GetMapping("users/{login:" + Constants.LOGIN_REGEX + "}")
    public ResponseEntity<UserDTO> getUser(@PathVariable String login) {
        log.debug("REST request to get User : {}", login);
        return ResponseUtil.wrapOrNotFound(userRepository.findOneWithCourseRolesAndAuthoritiesByLogin(login).map(user -> {
            user.setVisibleRegistrationNumber();
            return new UserDTO(user);
        }));
    }

    /**
     * POST users/import : Import multiple users to the user management
     * The passed list of UserDTOs must include at least one unique user identifier (i.e. registration number OR email OR login)
     * <p>
     * This method first tries to find the user in the internal Artemis user database (because the user is probably already using Artemis).
     * In case the user cannot be found, it additionally searches the connected LDAP in case it is configured.
     * <p>
     * For every user that is found, a non-null {@code isTestUser} value in the DTO is applied to that user, so test/QA accounts can be marked (or unmarked) via the user CSV
     * import and excluded from usage statistics. When the field is omitted, the existing flag is left unchanged.
     *
     * @param userDtos the list of users (with at one unique user identifier) who should be imported to Artemis
     * @return the list of users who could not be imported, because they could NOT be found in the Artemis database and could NOT be found in the connected LDAP
     */
    @PostMapping("users/import")
    public ResponseEntity<List<StudentDTO>> importUsers(@RequestBody List<StudentDTO> userDtos) {
        log.debug("REST request to import {} to Artemis", userDtos);
        List<StudentDTO> notFoundStudentsDtos = userService.importUsers(userDtos);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }

    /**
     * PUT users/:userId/sync-ldap : Updates an existing User based on the info available in the LDAP server.
     *
     * @param userId of the user to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated user
     */
    @PutMapping("users/{userId}/sync-ldap")
    public ResponseEntity<UserDTO> syncUserViaLdap(@PathVariable Long userId) {
        log.debug("REST request to update ldap information User : {}", userId);

        LdapUserService service = ldapUserService
                .orElseThrow(() -> new BadRequestAlertException("LDAP is not enabled on this Artemis instance.", "userManagement", "ldapNotEnabled"));

        var user = userRepository.findByIdWithCourseRolesAndAuthoritiesElseThrow(userId);
        service.loadUserDetailsFromLdap(user);
        var updatedUser = userCreationService.saveUser(user);

        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.updated", user.getLogin())).body(new UserDTO(updatedUser));
    }

    /**
     * GET users : get all users.
     *
     * @param userSearch the pagination information for user search
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("users")
    public ResponseEntity<List<UserDTO>> getAllUsers(UserPageableSearchDTO userSearch) {
        final Page<UserDTO> page = userRepository.getAllManagedUsers(userSearch);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET users/not-enrolled : get all logins of not enrolled users as a sorted list (no admins or Iris bot)
     *
     * @return the ResponseEntity with status 200 (OK) and with body all logins of not enrolled users
     */
    @GetMapping("users/not-enrolled")
    public ResponseEntity<List<String>> getNotEnrolledUsers() {
        List<String> logins = userRepository.findAllNotEnrolledUsers();
        return new ResponseEntity<>(logins, HttpStatus.OK);
    }

    /**
     * GET /users/authorities : get all authorities of the requesting user.
     *
     * @return the ResponseEntity with status 200 (OK) and with body a string list of the all the roles
     */
    @GetMapping("users/authorities")
    public ResponseEntity<List<String>> getAuthorities() {
        return ResponseEntity.ok(authorityRepository.getAuthorities());
    }

    @GetMapping("users/{login:" + Constants.LOGIN_REGEX + "}/deletion-impact")
    public ResponseEntity<UserDeletionImpactDTO> getUserDeletionImpact(@PathVariable String login) {
        User target = userRepository.findOneWithAuthoritiesByLogin(login).orElseThrow(() -> new EntityNotFoundException("User", login));
        checkDeletionTarget(target);
        return ResponseEntity.ok(userDeletionPlanService.createImpact(target, UserDeletionMode.ADMIN_FORCED));
    }

    @PostMapping("users/deletion-impact")
    public ResponseEntity<BulkUserDeletionImpactDTO> getBulkUserDeletionImpact(@Valid @RequestBody BulkUserDeletionImpactRequestDTO request) {
        List<User> targets = loadDeletionTargets(request.logins());
        return ResponseEntity.ok(userDeletionPlanService.createBulkImpact(targets, UserDeletionMode.ADMIN_FORCED));
    }

    /**
     * Permanently deletes a user after checking that the confirmed impact is still current.
     *
     * @param login   the login of the user to delete
     * @param request the impact fingerprint the administrator confirmed
     * @return the outcome of the deletion
     */
    @DeleteMapping("users/{login:" + Constants.LOGIN_REGEX + "}")
    public ResponseEntity<UserDeletionResultDTO> deleteUser(@PathVariable String login, @Valid @RequestBody PermanentUserDeletionRequestDTO request) {
        User target = userRepository.findOneWithAuthoritiesByLogin(login).orElseThrow(() -> new EntityNotFoundException("User", login));
        checkDeletionTarget(target);
        String actor = userRepository.getUser().getLogin();
        UserDeletionResultDTO result = permanentUserDeletionService.deleteByAdmin(target.getId(), request.impactFingerprint(), actor);
        if (result.status() == UserDeletionResultStatus.PLAN_CHANGED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        if (result.status() == UserDeletionResultStatus.FORBIDDEN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.deleted", login)).body(result);
    }

    /**
     * Permanently deletes users independently so one failed user cannot hide the outcome of the others.
     * <p>
     * The logins are passed in the request body on purpose: this is an internal admin bulk operation over an
     * unbounded list of identifiers (e.g. "delete all not-enrolled users"), which would otherwise overflow the
     * request-line / query-parameter limits if sent as query parameters. This endpoint is therefore intentionally
     * exempt from the "DELETE must not carry a body" convention.
     *
     * @param request confirmed users and their impact fingerprints
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("users")
    public ResponseEntity<List<UserDeletionResultDTO>> deleteUsers(@Valid @RequestBody BulkUserDeletionRequestDTO request) {
        log.debug("REST request to permanently delete {} users", request.users().size());
        String actor = userRepository.getUser().getLogin();
        List<UserDeletionResultDTO> results = new ArrayList<>();
        for (var confirmation : request.users()) {
            try {
                User target = userRepository.findOneWithAuthoritiesByLogin(confirmation.login()).orElseThrow(() -> new EntityNotFoundException("User", confirmation.login()));
                results.add(permanentUserDeletionService.deleteByAdmin(target.getId(), confirmation.impactFingerprint(), actor));
            }
            catch (Exception exception) {
                log.error("Permanent deletion failed for one user", exception);
                results.add(new UserDeletionResultDTO(null, confirmation.login(), UserDeletionResultStatus.FAILED, "deletionFailed"));
            }
        }
        long deleted = results.stream().filter(result -> result.status() == UserDeletionResultStatus.DELETED).count();
        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "artemisApp.userManagement.batch.deleted", Long.toString(deleted))).body(results);
    }

    private List<User> loadDeletionTargets(List<String> logins) {
        return logins.stream().distinct().map(login -> {
            User target = userRepository.findOneWithAuthoritiesByLogin(login).orElseThrow(() -> new EntityNotFoundException("User", login));
            checkDeletionTarget(target);
            return target;
        }).toList();
    }

    private void checkDeletionTarget(User target) {
        if (IRIS_BOT_LOGIN.equals(target.getLogin()) || Objects.equals(artemisInternalAdminUsername, target.getLogin())) {
            throw new BadRequestAlertException("This protected user cannot be deleted via the API.", "userManagement", "cannotDeleteProtectedUser");
        }
        if (userRepository.isCurrentUser(target.getLogin())) {
            throw new BadRequestAlertException("You cannot delete yourself", "userManagement", "cannotDeleteYourself");
        }
        if (AuthorizationCheckService.isAdmin(target.getAuthorities())) {
            throw new AccessForbiddenAlertException("Administrator accounts cannot be permanently deleted.", "userManagement", "cannotDeleteAdmin");
        }
    }
}
