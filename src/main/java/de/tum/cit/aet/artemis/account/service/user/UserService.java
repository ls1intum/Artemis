package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.account.domain.Authority.SUPER_ADMIN_AUTHORITY;
import static de.tum.cit.aet.artemis.account.domain.User.IRIS_BOT_LOGIN;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USER_EMAIL_DOMAIN_AFTER_SOFT_DELETE;
import static de.tum.cit.aet.artemis.core.config.Constants.USER_FIRST_NAME_AFTER_SOFT_DELETE;
import static de.tum.cit.aet.artemis.core.config.Constants.USER_LAST_NAME_AFTER_SOFT_DELETE;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;
import static de.tum.cit.aet.artemis.core.security.Role.SUPER_ADMIN;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.security.RandomUtil;
import de.tum.cit.aet.artemis.account.service.AccountCredentialRevocationService;
import de.tum.cit.aet.artemis.account.service.AccountSecurityNotificationService;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.service.UserRecoveryKeyService;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.ScienceEventApi;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.AccountRegistrationBlockedException;
import de.tum.cit.aet.artemis.core.exception.EmailAlreadyUsedException;
import de.tum.cit.aet.artemis.core.exception.PasswordViolatesRequirementsException;
import de.tum.cit.aet.artemis.core.exception.UsernameAlreadyUsedException;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localvc.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationSettingService;
import de.tum.cit.aet.artemis.notification.service.GlobalNotificationSettingService;
import de.tum.cit.aet.artemis.notification.service.UserCourseNotificationStatusService;
import de.tum.cit.aet.artemis.programming.domain.ParticipationVCSAccessToken;

/**
 * Service class for managing users.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Value("${artemis.user-management.internal-admin.username:#{null}}")
    private Optional<String> artemisInternalAdminUsername;

    @Value("${artemis.user-management.internal-admin.password:#{null}}")
    private Optional<String> artemisInternalAdminPassword;

    @Value("${artemis.user-management.internal-admin.email:#{null}}")
    private Optional<String> artemisInternalAdminEmail;

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final UserCourseRoleRepository userCourseRoleRepository;

    private final PasswordService passwordService;

    private final AuthorityService authorityService;

    private final Optional<LdapUserService> ldapUserService;

    private final AuthorityRepository authorityRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    private final UserRecoveryKeyService userRecoveryKeyService;

    private final FileService fileService;

    private final Optional<ScienceEventApi> scienceEventApi;

    private final ParticipationVcsAccessTokenService participationVCSAccessTokenService;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final SavedPostRepository savedPostRepository;

    private final AccountCredentialRevocationService accountCredentialRevocationService;

    private final AccountSecurityNotificationService accountSecurityNotificationService;

    private final CourseNotificationSettingService courseNotificationSettingService;

    private final UserCourseNotificationStatusService userCourseNotificationStatusService;

    private final GlobalNotificationSettingService globalNotificationSettingService;

    private final UserActivityService userActivityService;

    public UserService(UserCreationService userCreationService, UserRepository userRepository, UserCourseRoleRepository userCourseRoleRepository, AuthorityService authorityService,
            AuthorityRepository authorityRepository, Optional<LdapUserService> ldapUserService, PasswordService passwordService,
            InstanceMessageSendService instanceMessageSendService, FileService fileService, Optional<ScienceEventApi> scienceEventApi,
            ParticipationVcsAccessTokenService participationVCSAccessTokenService, Optional<LearnerProfileApi> learnerProfileApi, SavedPostRepository savedPostRepository,
            AccountCredentialRevocationService accountCredentialRevocationService, AccountSecurityNotificationService accountSecurityNotificationService,
            CourseNotificationSettingService courseNotificationSettingService, UserCourseNotificationStatusService userCourseNotificationStatusService,
            GlobalNotificationSettingService globalNotificationSettingService, UserRecoveryKeyService userRecoveryKeyService, UserActivityService userActivityService) {
        this.userCreationService = userCreationService;
        this.userRecoveryKeyService = userRecoveryKeyService;
        this.userActivityService = userActivityService;
        this.userRepository = userRepository;
        this.userCourseRoleRepository = userCourseRoleRepository;
        this.authorityService = authorityService;
        this.authorityRepository = authorityRepository;
        this.ldapUserService = ldapUserService;
        this.passwordService = passwordService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.fileService = fileService;
        this.scienceEventApi = scienceEventApi;
        this.participationVCSAccessTokenService = participationVCSAccessTokenService;
        this.learnerProfileApi = learnerProfileApi;
        this.savedPostRepository = savedPostRepository;
        this.accountCredentialRevocationService = accountCredentialRevocationService;
        this.accountSecurityNotificationService = accountSecurityNotificationService;
        this.courseNotificationSettingService = courseNotificationSettingService;
        this.userCourseNotificationStatusService = userCourseNotificationStatusService;
        this.globalNotificationSettingService = globalNotificationSettingService;
    }

    /**
     * Make sure that the internal artemis admin (in case it is defined in the yml configuration) is available in the database
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void applicationReady() {
        try {
            if (artemisInternalAdminUsername.isPresent() && artemisInternalAdminPassword.isPresent()) {
                // Startup work with nobody logged in, so db queries need the system principal.
                SecurityUtils.setSystemAuthorizationObject();
                ensureInternalAdminExists(artemisInternalAdminUsername.get(), artemisInternalAdminPassword.get());
            }
        }
        catch (Exception exception) {
            log.error("An error occurred after application startup when creating or updating the admin user or in the LDAP search", exception);
        }
    }

    /**
     * Ensures that an internal admin user exists with the specified credentials.
     * Creates the user if it doesn't exist, or updates its password and authorities if it does.
     * This method assumes credentials have already been validated by ConfigurationValidator.
     *
     * @param internalAdminUsername the username for the admin user
     * @param internalAdminPassword the password for the admin user
     */
    public void ensureInternalAdminExists(String internalAdminUsername, String internalAdminPassword) {
        log.debug("Ensuring internal admin user exists: {}", internalAdminUsername);

        Optional<User> existingInternalAdmin = userRepository.findOneWithAuthoritiesByLogin(internalAdminUsername);
        if (existingInternalAdmin.isPresent()) {
            log.info("Update internal admin user {}", internalAdminUsername);
            User internalAdmin = existingInternalAdmin.get();
            if (!internalAdmin.isInternal()) {
                // An instance that started up between #13394 and this change has an admin that was created as an externally
                // managed account and therefore cannot use the password configured for it. Creating it correctly is not
                // enough for those instances: the account exists, so only this branch is reached. The account belongs to
                // Artemis by configuration and its password is set right here on every startup, so owning the flag as well
                // is what makes that configuration mean something.
                //
                // Warned rather than logged quietly, and spelled out, because the flag decides more than the password check:
                // LdapAuthenticationProvider skips internal users, so this account stops authenticating against the
                // directory, and prepareUserForPasswordReset accepts internal users, so it becomes eligible for the e-mail
                // password reset. For the dedicated local admin this property is meant for, all of that is intended. An
                // operator who pointed it at a directory account instead needs to see it.
                log.warn("The configured internal admin {} exists as an externally managed account and is now marked internal: it will authenticate with the "
                        + "configured password instead of the external directory, and it becomes eligible for the Artemis password reset. The flag cannot be "
                        + "changed back through the admin UI, which offers it only while creating a user. Point "
                        + "artemis.user-management.internal-admin.username at a dedicated local account if that is not what you want.", internalAdminUsername);
                internalAdmin.setInternal(true);
            }
            internalAdmin.setActivated(true);
            applyConfiguredInternalAdminEmail(internalAdmin);
            // The configured password is applied on every startup, so it is compared rather than written blindly: stamping
            // credentialsChangedDate unconditionally would end every admin session on every restart, while never stamping it
            // leaves sessions from before a rotated configured password renewable past the renewal checkpoint.
            boolean internalAdminPasswordChanged = internalAdmin.getPassword() == null || !passwordService.checkPasswordMatch(internalAdminPassword, internalAdmin.getPassword());
            if (internalAdminPasswordChanged) {
                internalAdmin.setPassword(passwordService.hashPassword(internalAdminPassword));
            }
            // needs to be mutable --> new HashSet<>(Set.of(...))
            internalAdmin.setAuthorities(new HashSet<>(Set.of(SUPER_ADMIN_AUTHORITY, new Authority(STUDENT.getAuthority()))));
            User savedInternalAdmin = saveUser(internalAdmin);
            // Stamped after the save, and from what the save returned: the timestamp is keyed on the id, and reading it
            // back off the argument would depend on whether the save persisted or merged.
            if (internalAdminPasswordChanged) {
                userActivityService.recordCredentialsChanged(savedInternalAdmin.getId(), Instant.now());
            }
        }
        else {
            log.info("Create internal admin user {}", internalAdminUsername);
            final var managedUserVM = createManagedUserVm(internalAdminUsername, internalAdminPassword);
            // The configured address is one fixed value - and defaults to a placeholder - so it can already belong to another account: a previous internal admin that was
            // renamed, or imported data. Since emails have to be unique, creating the account would be refused, and refusing the emergency account over an address it does
            // not need is the wrong trade-off. It is created without one instead, and the operator is told which setting to point at a free address.
            if (StringUtils.hasText(managedUserVM.getEmail()) && userRepository.existsByEmailIgnoreCase(managedUserVM.getEmail())) {
                log.warn("The email address {} configured for the internal admin already belongs to another account, so {} is created without an email address. "
                        + "Point artemis.user-management.internal-admin.email at an unused address to give it one.", managedUserVM.getEmail(), internalAdminUsername);
                managedUserVM.setEmail(null);
            }
            userCreationService.createUser(managedUserVM);
        }
    }

    /**
     * Gives the existing internal admin the configured address once that address is free.
     *
     * <p>
     * The creation path below drops a configured address that already belongs to someone else and tells the operator to
     * point the setting at an unused one. That advice only means something if a later startup acts on it, which is what
     * this does: the address is applied on every startup the way the password is, and an address that is still taken is
     * reported again rather than silently ignored. Nothing is cleared when the setting is removed - an admin that lost
     * its address would lose the password reset with it, and unsetting a property should not do that.
     *
     * @param internalAdmin the existing internal admin account
     */
    private void applyConfiguredInternalAdminEmail(User internalAdmin) {
        String configuredEmail = User.canonicalEmail(artemisInternalAdminEmail.orElse(null));
        if (configuredEmail == null || configuredEmail.equalsIgnoreCase(internalAdmin.getEmail())) {
            return;
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(configuredEmail, internalAdmin.getId())) {
            log.warn(
                    "The email address {} configured for the internal admin belongs to another account, so {} keeps {}. Point "
                            + "artemis.user-management.internal-admin.email at an unused address.",
                    configuredEmail, internalAdmin.getLogin(), internalAdmin.getEmail() == null ? "no email address" : internalAdmin.getEmail());
            return;
        }
        log.info("Assigning the configured email address {} to the internal admin {}", configuredEmail, internalAdmin.getLogin());
        internalAdmin.setEmail(configuredEmail);
    }

    private ManagedUserVM createManagedUserVm(String login, String password) {
        ManagedUserVM userDto = new ManagedUserVM();
        userDto.setLogin(login);
        userDto.setPassword(password);
        userDto.setActivated(true);
        // Set explicitly because #13394 made the flag caller-controlled - UserCreationService.createUser used to force
        // internal = true for everyone - and UserDTO defaults it to false. Without this the admin is stored as an externally
        // managed account and the password configured right here is unusable, because
        // ArtemisInternalAuthenticationProvider only ever looks for internal users. The update branch above repairs an
        // account that was created in between.
        userDto.setInternal(true);
        userDto.setFirstName("Administrator");
        userDto.setLastName("Administrator");
        userDto.setEmail(artemisInternalAdminEmail.orElse("admin@localhost"));
        userDto.setLangKey("en");
        userDto.setCreatedBy("system");
        userDto.setLastModifiedBy("system");
        // needs to be mutable --> new HashSet<>(Set.of(...))
        userDto.setAuthorities(new HashSet<>(Set.of(SUPER_ADMIN.getAuthority(), STUDENT.getAuthority())));
        return userDto;
    }

    /**
     * Activate user registration
     *
     * @param key activation key for user registration
     * @return user if user exists otherwise null
     */
    public Optional<User> activateRegistration(String key) {
        log.debug("Activating user for activation key {}", key);
        return userRecoveryKeyService.findUserIdByActivationKey(key).flatMap(userRepository::findById).map(user -> {
            activateUser(user);
            return user;
        });
    }

    /**
     * Activates the user and cancels the automatic cleanup of the account.
     *
     * @param user the non-activated user
     */
    public void activateUser(User user) {
        // Cancel automatic removal of the user since it's activated.
        instanceMessageSendService.sendCancelRemoveNonActivatedUserSchedule(user.getId());
        // activate given user for the registration key.
        userCreationService.activateUser(user);
    }

    /**
     * Reset user password for given reset key
     *
     * @param newPassword      new password string
     * @param key              reset key
     * @param revocationChoice which of the user's other credentials to revoke alongside the reset
     * @return user for whom the password was performed
     */
    public Optional<User> completePasswordReset(String newPassword, String key, CredentialRevocationChoiceDTO revocationChoice) {
        log.debug("Reset user password for reset key {}", key);
        return userRecoveryKeyService.findByResetKey(key).filter(row -> row.getResetDate() != null && row.getResetDate().isAfter(Instant.now().minusSeconds(86400)))
                .flatMap(row -> userRepository.findById(row.getUserId())).map(user -> {
                    user.setPassword(passwordService.hashPassword(newPassword));
                    userRecoveryKeyService.clearResetKey(user.getId());
                    saveUser(user);
                    // Stops sessions established before the reset from being extended any further.
                    userActivityService.recordCredentialsChanged(user.getId(), Instant.now());
                    // A reset is the recovery flow, but forgetting a password is not the same as losing it to someone else, and
                    // re-enrolling every authenticator and key is a real cost to impose on the common case. So the user decides,
                    // exactly as they do when changing a password from inside the account - with the difference that a reset
                    // defaults to revoking everything (see KeyAndPasswordVM#revokeCredentialsOrAll), because completing one
                    // only proves control of the mailbox.
                    accountCredentialRevocationService.revokeSelectedCredentials(user, revocationChoice, "password reset completed");
                    accountSecurityNotificationService.passwordChanged(user, revocationChoice, AccountSecurityNotificationService.PasswordChangeActor.RESET);
                    return user;
                });
    }

    /**
     * Saves the user.
     *
     * @param user the user object that will be saved into the database
     * @return the saved and potentially updated user object
     */
    public User saveUser(User user) {
        log.debug("Save user {}", user);
        return userRepository.save(user);
    }

    /**
     * Set password reset data for a user if eligible
     *
     * @param user user requesting reset
     * @return true if the user is eligible
     */
    public boolean prepareUserForPasswordReset(User user) {
        if (user.getActivated() && user.isInternal()) {
            userRecoveryKeyService.storeResetKey(user.getId(), RandomUtil.generateResetKey(), Instant.now());
            return true;
        }
        return false;
    }

    /**
     * Register user and create it only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param userDTO  user data transfer object
     * @param password string
     * @return newly registered user or throw registration exception
     */
    public User registerUser(UserDTO userDTO, String password) {
        // Prepare the new user object.
        final var newUser = new User();
        String passwordHash = passwordService.hashPassword(password);
        newUser.setLogin(userDTO.getLogin().toLowerCase(Locale.ENGLISH));
        if (IRIS_BOT_LOGIN.equals(newUser.getLogin())) {
            throw new UsernameAlreadyUsedException();
        }
        // new user gets initially a generated password
        newUser.setPassword(passwordHash);
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());
        newUser.setEmail(userDTO.getEmail());
        newUser.setImageUrl(userDTO.getImageUrl());
        newUser.setLangKey(userDTO.getLangKey());
        // new user is not active
        newUser.setActivated(false);
        // registered users are always internal
        newUser.setInternal(true);
        // new user gets registration key
        // The key is stored after the user is saved, since it is keyed on the user id.
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(STUDENT.getAuthority()).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);

        // Find user that has the same login
        Optional<User> optionalExistingUser = userRepository.findOneByLogin(userDTO.getLogin().toLowerCase(Locale.ENGLISH));
        if (optionalExistingUser.isPresent()) {
            User existingUser = optionalExistingUser.get();
            return handleRegisterUserWithSameLoginAsExistingUser(newUser, existingUser);
        }

        // Do not use a single-result lookup here: installations can still contain legacy duplicate emails during the preparation phase.
        if (newUser.getEmail() != null && userRepository.existsByEmailIgnoreCase(newUser.getEmail())) {
            throw new EmailAlreadyUsedException();
        }

        // we need to save first so that the user can be found in the database in the subsequent method
        User savedNonActivatedUser = saveUser(newUser);
        userRecoveryKeyService.storeActivationKey(savedNonActivatedUser.getId(), RandomUtil.generateActivationKey());

        // Automatically remove the user if it wasn't activated after a certain amount of time.
        instanceMessageSendService.sendRemoveNonActivatedUserSchedule(savedNonActivatedUser.getId());

        log.debug("Created Information for User: {}", newUser);
        return newUser;
    }

    /**
     * Handles the case where a user registers a new account but a user with the same login already
     * exists in Artemis.
     *
     * @param newUser      the new user
     * @param existingUser the existing user
     * @return the existing non-activated user in Artemis.
     */
    private User handleRegisterUserWithSameLoginAsExistingUser(User newUser, User existingUser) {
        // An account with the same login is already activated.
        if (existingUser.getActivated()) {
            throw new UsernameAlreadyUsedException();
        }

        // The user has the same login and email, but the account is not activated.
        // Return the existing non-activated user so that Artemis can re-send the
        // activation link.
        // Null-safe since canonicalEmail turns a blank address into null: an account registered without one must
        // still get its activation link resent rather than a NullPointerException.
        if (Objects.equals(User.canonicalEmail(existingUser.getEmail()), newUser.getEmail())) {
            // Update the existing user and VCS
            newUser.setId(existingUser.getId());
            User updatedExistingUser = userRepository.save(newUser);

            // Post-pone the cleaning up of the account
            instanceMessageSendService.sendRemoveNonActivatedUserSchedule(updatedExistingUser.getId());
            return updatedExistingUser;
        }

        // The email is different which means that the user wants to re-register the same
        // account with a different email. Block this.
        throw new AccountRegistrationBlockedException(existingUser.getEmail());
    }

    /**
     * Creates a new Artemis user from LDAP in case this is active and a user with the login can be found
     *
     * @param login the login of the user
     * @return a new user or null if the LDAP user was not found
     */
    public Optional<User> createUserFromLdapWithLogin(String login) {
        return findUserInLdap(login, () -> ldapUserService.orElseThrow().findByLogin(login));
    }

    /**
     * Creates a new Artemis user from LDAP in case this is active and a user with the email can be found
     *
     * @param email the email of the user
     * @return a new user or null if the LDAP user was not found
     */
    public Optional<User> createUserFromLdapWithEmail(String email) {
        return findUserInLdap(email, () -> ldapUserService.orElseThrow().findByAnyEmail(email));
    }

    /**
     * Creates a new Artemis user from LDAP in case this is active and a user with the registration number can be found
     *
     * @param registrationNumber the matriculation number of the user
     * @return a new user or null if the LDAP user was not found
     */
    public Optional<User> createUserFromLdapWithRegistrationNumber(String registrationNumber) {
        return findUserInLdap(registrationNumber, () -> ldapUserService.orElseThrow().findByRegistrationNumber(registrationNumber));
    }

    /**
     * Searches the (optional) LDAP service for a user with the given unique user identifier (e.g. login, email, registration number) and supplier function
     * and returns a new Artemis user.
     * Note: this method should only be used if the user does not yet exist in the database
     * <p>
     * The account is created externally managed and activated: it authenticates against the directory, so Artemis has no
     * activation step to offer it. Creating it unactivated instead used to leave imported students unable to use their
     * repositories - see {@link User#activated}.
     *
     * @param userIdentifier       the userIdentifier of the user (e.g. login, email, registration number)
     * @param userSupplierFunction the function that supplies the user, typically a call to ldapUserService, e.g. "() -> ldapUserService.orElseThrow().findByLogin(email)"
     * @return a new user or null if the LDAP user was not found
     */
    private Optional<User> findUserInLdap(String userIdentifier, Supplier<Optional<LdapUserDto>> userSupplierFunction) {
        if (!StringUtils.hasText(userIdentifier)) {
            return Optional.empty();
        }
        if (ldapUserService.isPresent()) {
            Optional<LdapUserDto> ldapUserOptional = userSupplierFunction.get();
            if (ldapUserOptional.isPresent()) {
                LdapUserDto ldapUser = ldapUserOptional.get();
                log.info("Ldap User {} has login: {}", ldapUser.getFirstName() + " " + ldapUser.getFirstName(), ldapUser.getLogin());

                // handle edge case, the user already exists in Artemis, but for some reason the values differ
                if (StringUtils.hasText(ldapUser.getLogin())) {
                    // load the user with authorities because they might be needed later
                    var existingUser = userRepository.findOneWithAuthoritiesByLogin(ldapUser.getLogin());
                    if (existingUser.isPresent()) {
                        ldapUserService.orElseThrow().syncUserDetails(existingUser.get(), ldapUser);
                        saveUser(existingUser.get());
                        return existingUser;
                    }
                }

                // Use empty password, so that we don't store the credentials of external users in the Artemis DB
                User user = userCreationService.createUser(ldapUser.getLogin(), "", ldapUser.getFirstName(), ldapUser.getLastName(), ldapUser.getEmail(),
                        ldapUser.getRegistrationNumber(), null, "en", false);
                // load the user with authorities because they might be needed later
                return userRepository.findOneWithAuthoritiesById(user.getId());
            }
            else {
                log.warn("Ldap User with userIdentifier '{}' not found", userIdentifier);
            }
        }
        return Optional.empty();
    }

    /**
     * Legacy implementation retained temporarily for compatibility tests and migrations. Production deletion paths must
     * use {@code PermanentUserDeletionService}; no new tombstones may be created. Remove this method together with the
     * {@code is_deleted} compatibility column after legacy tombstones have drained.
     *
     * @param login user login string
     */
    @Deprecated(forRemoval = true)
    public void softDeleteUser(String login) {
        userRepository.findOneByLogin(login).ifPresent(user -> {
            // Covers the participation and repository tokens and the SSH keys this method used to delete individually,
            // and additionally the passkeys and the personal VCS access token, which it did not.
            accountCredentialRevocationService.revokeAllCredentials(user, "user soft deleted");
            // A reset or activation mail sent before the deletion must not remain a way into the anonymised account.
            userRecoveryKeyService.clearAll(user.getId());
            learnerProfileApi.ifPresent(api -> api.deleteProfile(user));
            globalNotificationSettingService.deleteAllByUserId(user.getId());
            userCourseRoleRepository.deleteByUser_Id(user.getId());
            user.setDeleted(true);
            user.setLearnerProfile(null);
            anonymizeUser(user);
            log.warn("Soft Deleted User: {}", user);
        });
    }

    /**
     * Sets the properties of the user to random or dummy values, making it impossible to identify the user.
     * Also updates the user in connectors and auth provider.
     *
     * @param user the user that should be anonymized
     */
    protected void anonymizeUser(User user) {
        final String originalLogin = user.getLogin();
        final String randomPassword = RandomUtil.generatePassword();
        final String userImageString = user.getImageUrl();
        final String anonymizedLogin = lowerCase(RandomUtil.generateRandomAlphanumericString(), Locale.ENGLISH);

        user.setFirstName(USER_FIRST_NAME_AFTER_SOFT_DELETE);
        user.setLastName(USER_LAST_NAME_AFTER_SOFT_DELETE);
        user.setLogin(anonymizedLogin);
        user.setPassword(randomPassword);
        user.setEmail(RandomUtil.generateRandomAlphanumericString() + USER_EMAIL_DOMAIN_AFTER_SOFT_DELETE);
        user.setRegistrationNumber(null);
        user.setImageUrl(null);
        user.setActivated(false);

        List<SavedPost> savedPostsOfUser = savedPostRepository.findSavedPostsByUserId(user.getId());

        if (!savedPostsOfUser.isEmpty()) {
            savedPostRepository.deleteAll(savedPostsOfUser);
        }

        userCourseNotificationStatusService.deleteAllForUser(user.getId());
        courseNotificationSettingService.deleteAllForUser(user.getId());

        userRepository.save(user);
        userRepository.flush();

        scienceEventApi.ifPresent(api -> api.renameIdentity(originalLogin, anonymizedLogin));

        if (userImageString != null) {
            fileService.schedulePathForDeletion(new FileSystemLocation.ProfilePicture(userImageString).path(), 0);
        }
    }

    /**
     * Trys to find a user by the internal admin username
     *
     * @return an Optional.emtpy() if no internal admin user is found, otherwise an optional with the internal admin user
     */
    public Optional<User> findInternalAdminUser() {
        if (artemisInternalAdminUsername.isEmpty()) {
            log.warn("The internal admin username is not configured and no internal admin user can be retrieved.");
            return Optional.empty();
        }
        return userRepository.findOneByLogin(artemisInternalAdminUsername.get());
    }

    /**
     * Change password of current user, revoking the credential types the user selected along with it.
     *
     * @param currentClearTextPassword cleartext password
     * @param newPassword              new password string
     * @param revocationChoice         which of the user's other credentials to revoke; only the user knows whether the old
     *                                     password may have been seen by someone else, which is what decides this
     */
    public void changePassword(String currentClearTextPassword, String newPassword, CredentialRevocationChoiceDTO revocationChoice) {
        SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneByLogin).ifPresent(user -> {
            String currentPasswordHash = user.getPassword();
            if (!passwordService.checkPasswordMatch(currentClearTextPassword, currentPasswordHash)) {
                throw new PasswordViolatesRequirementsException();
            }
            String newPasswordHash = passwordService.hashPassword(newPassword);
            user.setPassword(newPasswordHash);
            saveUser(user);
            userActivityService.recordCredentialsChanged(user.getId(), Instant.now());
            // What else is revoked is the user's decision: only they know whether the old password may have been seen by
            // someone else, and that is what decides whether losing their enrolled authenticators and keys is warranted.
            accountCredentialRevocationService.revokeSelectedCredentials(user, revocationChoice, "password changed");
            accountSecurityNotificationService.passwordChanged(user, revocationChoice, AccountSecurityNotificationService.PasswordChangeActor.OWNER);

            log.debug("Changed password for User: {}", user);
        });
    }

    /**
     * Check the username and password for validity. Throws Exception if invalid.
     *
     * @param username The username to check
     * @param password The password to check
     */
    public void checkUsernameAndPasswordValidityElseThrow(String username, String password) {
        checkUsernameOrThrow(username);
        checkNullablePasswordOrThrow(password);
    }

    private void checkUsernameOrThrow(String username) {
        if (username == null || username.length() < USERNAME_MIN_LENGTH) {
            throw new AccessForbiddenException("The username has to be at least " + USERNAME_MIN_LENGTH + " characters long");
        }
        else if (username.length() > USERNAME_MAX_LENGTH) {
            throw new AccessForbiddenException("The username has to be less than " + USERNAME_MAX_LENGTH + " characters long");
        }
    }

    /**
     * <p>
     * The password can be null, then a random one will be generated ({@code Create}) or it won't be changed ({@code Update}).
     * <p>
     * If the password is not null, its length has to be at least {@code PASSWORD_MIN_LENGTH}.
     *
     * @param password The password to check
     */
    private void checkNullablePasswordOrThrow(String password) {
        if (password == null) {
            return;
        }
        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new AccessForbiddenException("The password has to be at least " + PASSWORD_MIN_LENGTH + " characters long");
        }
        if (password.length() > PASSWORD_MAX_LENGTH) {
            throw new AccessForbiddenException("The password has to be less than " + PASSWORD_MAX_LENGTH + " characters long");
        }
    }

    /**
     * Add the user to a course with the given role.
     *
     * @param user   the user to add
     * @param course the course to add the user to
     * @param role   the role the user should have in the course
     */
    public void addUserToCourse(User user, Course course, CourseRole role) {
        log.debug("Add user {} to course {} with role {}", user.getLogin(), course.getId(), role);
        // Idempotent: if the user already holds this role, there is nothing to write and the (coarse, global) authorities
        // cannot change — skip the reload + authority rebuild + save. This keeps bulk re-enrollment cheap.
        if (userCourseRoleRepository.existsByUser_IdAndCourse_IdAndRole(user.getId(), course.getId(), role)) {
            return;
        }
        userCourseRoleRepository.save(new UserCourseRole(user, course, role));
        // ROLE_STUDENT is always granted, so adding a STUDENT role never changes the global authorities; only a newly
        // granted TA/EDITOR/INSTRUCTOR role can. Rebuild authorities only when they could actually change.
        if (role != CourseRole.STUDENT) {
            user = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
            user.setAuthorities(authorityService.buildAuthorities(user));
            saveUser(user);
        }
    }

    /**
     * Batch variant of {@link #addUserToCourse(User, Course, CourseRole)} for bulk enrollment (CSV import, bulk exam
     * registration). Replaces one existsBy query + insert (+ authority rebuild) per user with a single batch
     * existence check, a single batch insert, and — if {@code role != STUDENT} — a single batch authority rebuild.
     *
     * @param users  the users to add; users who already hold the role in the course are skipped
     * @param course the course to add the users to
     * @param role   the role the users should have in the course
     */
    public void addUsersToCourse(List<User> users, Course course, CourseRole role) {
        if (users.isEmpty()) {
            return;
        }
        log.debug("Add {} users to course {} with role {}", users.size(), course.getId(), role);

        Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        Set<Long> alreadyEnrolledIds = userCourseRoleRepository.findUserIdsByCourse_IdAndRoleAndUser_IdIn(course.getId(), role, userIds);
        // Deduplicate by user id: duplicate rows in the import/request body must not produce two UserCourseRole entities
        // with the same composite key (user, course, role), which would fail the whole bulk enrollment.
        Set<Long> seenIds = new HashSet<>();
        List<User> newlyEnrolled = users.stream().filter(user -> !alreadyEnrolledIds.contains(user.getId())).filter(user -> seenIds.add(user.getId())).toList();
        if (newlyEnrolled.isEmpty()) {
            return;
        }
        userCourseRoleRepository.saveAll(newlyEnrolled.stream().map(user -> new UserCourseRole(user, course, role)).toList());

        // ROLE_STUDENT is always granted, so adding a STUDENT role never changes the global authorities; only a newly
        // granted TA/EDITOR/INSTRUCTOR role can. Rebuild authorities only when they could actually change.
        if (role != CourseRole.STUDENT) {
            Set<String> logins = newlyEnrolled.stream().map(User::getLogin).collect(Collectors.toSet());
            List<User> usersWithAuthorities = new ArrayList<>(userRepository.findAllWithAuthoritiesByDeletedIsFalseAndLoginIn(logins));
            Map<Long, Set<Authority>> rebuiltAuthorities = authorityService.buildAuthoritiesForUsers(usersWithAuthorities);
            usersWithAuthorities.forEach(user -> user.setAuthorities(rebuiltAuthorities.get(user.getId())));
            userRepository.saveAll(usersWithAuthorities);
        }
    }

    /**
     * Remove the user from a course role.
     *
     * @param user   the user to remove
     * @param course the course from which the user should be removed
     * @param role   the role to revoke
     */
    public void removeUserFromCourse(User user, Course course, CourseRole role) {
        log.info("Remove user {} from course {} role {}", user.getLogin(), course.getId(), role);
        userCourseRoleRepository.deleteByUser_IdAndCourse_IdAndRole(user.getId(), course.getId(), role);
        // ROLE_STUDENT is always granted, so revoking a STUDENT role never changes the global authorities; only revoking
        // a TA/EDITOR/INSTRUCTOR role can. Rebuild authorities only when they could actually change.
        if (role != CourseRole.STUDENT) {
            user = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
            user.setAuthorities(authorityService.buildAuthorities(user));
            saveUser(user);
        }
    }

    /**
     * Resolves a student from any combination of registration number, login and email, for the course member, exam and admin
     * user imports. Looks in the Artemis database first, because the user is most probably already using Artemis, and only
     * then in the configured LDAP - from which a missing account is created.
     * <p>
     * The whole database is searched before the directory is consulted at all, and within each of the two the identifiers are
     * tried in the order <b>login, email, registration number</b>, stopping at the first match. Blank identifiers are skipped,
     * and all three being blank returns empty immediately.
     * <p>
     * An account created from the directory here is created activated, like one created on first login - see
     * {@link User#activated}.
     *
     * @param registrationNumber the registration number of the user
     * @param login              the login of the user
     * @param email              the email of the user
     * @return the found student, otherwise returns an empty optional
     */
    public Optional<User> findUser(@Nullable String registrationNumber, @Nullable String login, @Nullable String email) {
        if (!StringUtils.hasText(login) && !StringUtils.hasText(email) && !StringUtils.hasText(registrationNumber)) {
            // if none of the three values is specified, the user cannot be found
            return Optional.empty();
        }
        try {
            var optionalUser = findUserInDatabase(registrationNumber, login, email);
            if (optionalUser.isEmpty()) {
                // In this case, the user was NOT found in the database! We can try to create it from the external user management, in case it is configured
                optionalUser = findUserInLdap(registrationNumber, login, email);
            }

            if (optionalUser.isPresent()) {
                return optionalUser;
            }

            log.warn("User with registration number '{}', login '{}' and email '{}' NOT found in Artemis user database NOR in connected LDAP", registrationNumber, login, email);
        }
        catch (Exception ex) {
            log.warn("Error while trying to find user with registration number {}, login {}, email {}", registrationNumber, login, email, ex);
        }
        return Optional.empty();
    }

    private Optional<User> findUserInDatabase(@Nullable String registrationNumber, @Nullable String login, @Nullable String email) {
        Optional<User> optionalUser = Optional.empty();
        if (StringUtils.hasText(login)) {
            optionalUser = userRepository.findUserWithAuthoritiesByLogin(login);
        }
        if (optionalUser.isEmpty() && StringUtils.hasText(email)) {
            optionalUser = userRepository.findUserWithAuthoritiesByEmail(email);
        }
        if (optionalUser.isEmpty() && StringUtils.hasText(registrationNumber)) {
            optionalUser = userRepository.findUserWithAuthoritiesByRegistrationNumber(registrationNumber);
        }
        return optionalUser;
    }

    private Optional<User> findUserInLdap(@Nullable String registrationNumber, @Nullable String login, @Nullable String email) {
        Optional<User> optionalUser = Optional.empty();
        if (StringUtils.hasText(login)) {
            optionalUser = createUserFromLdapWithLogin(login);
        }
        if (optionalUser.isEmpty() && StringUtils.hasText(email)) {
            optionalUser = createUserFromLdapWithEmail(email);
        }
        if (optionalUser.isEmpty() && StringUtils.hasText(registrationNumber)) {
            optionalUser = createUserFromLdapWithRegistrationNumber(registrationNumber);
        }
        return optionalUser;
    }

    public void updateUserLanguageKey(Long userId, String languageKey) {
        userRepository.updateUserLanguageKey(userId, languageKey);
    }

    /**
     * This method first tries to find each user of the given list. When a user is found and the DTO explicitly provides
     * the {@code isTestUser} flag (i.e. it is not {@code null}), the flag is applied to the user so that test/QA accounts
     * can be marked (or unmarked) via the user CSV import and excluded from usage statistics.
     *
     * @param userDtos users to be looked up (and optionally flagged as test users)
     * @return a list of not found users
     */
    public List<StudentDTO> importUsers(List<StudentDTO> userDtos) {
        List<StudentDTO> notFoundUsers = new ArrayList<>();
        for (var userDto : userDtos) {
            var optionalStudent = findUser(userDto.registrationNumber(), userDto.login(), userDto.email());
            if (optionalStudent.isEmpty()) {
                notFoundUsers.add(userDto);
            }
            else if (userDto.isTestUser() != null && optionalStudent.get().isTestUser() != userDto.isTestUser()) {
                User student = optionalStudent.get();
                student.setTestUser(userDto.isTestUser());
                userRepository.save(student);
            }
        }

        return notFoundUsers;
    }

    /**
     * Get the vcs access token associated with a user and a participation
     *
     * @param user            the user associated with the vcs access token
     * @param participationId the participation's participationId associated with the vcs access token
     *
     * @return the users participation vcs access token, or throws an exception if it does not exist
     */
    public ParticipationVCSAccessToken getParticipationVcsAccessTokenForUserAndParticipationIdOrElseThrow(User user, Long participationId) {
        return participationVCSAccessTokenService.findByUserAndParticipationIdOrElseThrow(user, participationId);
    }

    /**
     * Create a vcs access token associated with a user and a participation, and return it
     *
     * @param user            the user associated with the vcs access token
     * @param participationId the participation's participationId associated with the vcs access token
     *
     * @return the users newly created participation vcs access token, or throws an exception if it already existed
     */
    public ParticipationVCSAccessToken createParticipationVcsAccessTokenForUserAndParticipationIdOrElseThrow(User user, Long participationId) {
        return participationVCSAccessTokenService.createVcsAccessTokenForUserAndParticipationIdOrElseThrow(user, participationId);
    }
}
