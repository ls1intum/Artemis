package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.Organization;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.account.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.security.RandomUtil;
import de.tum.cit.aet.artemis.account.service.AccountCredentialRevocationService;
import de.tum.cit.aet.artemis.account.service.AccountSecurityNotificationService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserCreationService {

    private static final Logger log = LoggerFactory.getLogger(UserCreationService.class);

    /**
     * Duplicated from {@link de.tum.cit.aet.artemis.account.service.AccountService} on purpose: that service already depends on this one, so injecting it here
     * would create a circular dependency. {@link UserManagementInfoContributor} reads the same property the same way.
     */
    @Value("${artemis.user-management.registration.enabled:#{null}}")
    private Optional<Boolean> registrationEnabled;

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final AuthorityRepository authorityRepository;

    private final OrganizationRepository organizationRepository;

    private final AccountCredentialRevocationService accountCredentialRevocationService;

    private final AccountSecurityNotificationService accountSecurityNotificationService;

    private final AuditEventRepository auditEventRepository;

    public UserCreationService(UserRepository userRepository, PasswordService passwordService, AuthorityRepository authorityRepository,
            OrganizationRepository organizationRepository, AccountCredentialRevocationService accountCredentialRevocationService,
            AccountSecurityNotificationService accountSecurityNotificationService, AuditEventRepository auditEventRepository) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.authorityRepository = authorityRepository;
        this.organizationRepository = organizationRepository;
        this.accountCredentialRevocationService = accountCredentialRevocationService;
        this.accountSecurityNotificationService = accountSecurityNotificationService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Create user only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     * <p>
     * The account is created <b>activated</b> unless its own owner is expected to activate it, which needs {@code isInternal}
     * <em>and</em> self-registration to be enabled on this instance. Only then does it get {@code activated = false} and an
     * activation key. See {@link User#activated} for why an externally managed account must never be created unactivated, and
     * {@link #isRegistrationEnabled()} for the property involved.
     *
     * @param login              user login string
     * @param password           user password, if set to null, the password will be set randomly
     * @param firstName          first name of user
     * @param lastName           last name of the user
     * @param email              email of the user
     * @param registrationNumber the matriculation number of the student*
     * @param imageUrl           user image url
     * @param langKey            user language
     * @param isInternal         true if the actual password gets saved in the database
     * @return newly created user, activated unless it is an internal account awaiting self-activation
     */
    public User createUser(String login, @Nullable String password, String firstName, String lastName, String email, @Nullable String registrationNumber, String imageUrl,
            String langKey, boolean isInternal) {
        User newUser = new User();

        if (isInternal) {
            // Set random password for null passwords
            if (password == null) {
                password = RandomUtil.generatePassword();
            }
            String passwordHash = passwordService.hashPassword(password);
            // new user gets initially a generated password
            newUser.setPassword(passwordHash);
        }

        newUser.setLogin(login);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setEmail(email);
        // an empty string is considered as null to satisfy the unique constraint on registration number
        if (StringUtils.hasText(registrationNumber)) {
            newUser.setRegistrationNumber(registrationNumber);
        }
        newUser.setImageUrl(imageUrl);
        newUser.setLangKey(langKey);
        // Only create the user unactivated when they can actually activate themselves, which needs both an internal account and the
        // self-registration feature. The activation key is redeemable exclusively through GET /activate, and both that endpoint and the
        // mail carrying the key are gated behind artemis.user-management.registration.enabled. An externally managed (LDAP/SAML) user
        // therefore never receives a key and could never redeem one, so creating them unactivated left behind accounts that nothing
        // could ever activate - which stayed invisible only because the LDAP provider does not check `activated`, until the git
        // authentication paths started enforcing it and locked those users out of their repositories.
        if (isInternal && isRegistrationEnabled()) {
            newUser.setActivated(false);
            newUser.setActivationKey(RandomUtil.generateActivationKey());
        }
        else {
            newUser.setActivated(true);
        }
        newUser.setInternal(isInternal);

        final var authority = authorityRepository.findById(STUDENT.getAuthority()).orElseThrow();
        // needs to be mutable --> new HashSet<>(Set.of(...))
        final var authorities = new HashSet<>(Set.of(authority));
        newUser.setAuthorities(authorities);
        try {
            Set<Organization> matchingOrganizations = organizationRepository.getAllMatchingOrganizationsByUserEmail(email);
            newUser.setOrganizations(matchingOrganizations);
        }
        catch (InvalidDataAccessApiUsageException | PatternSyntaxException pse) {
            log.warn("Could not retrieve matching organizations from pattern: {}", pse.getMessage());
        }
        newUser = saveUser(newUser);
        log.debug("Created user: {}", newUser);
        return newUser;
    }

    /**
     * The self-registration feature is only enabled when artemis.user-management.registration.enabled is explicitly set to true. A missing entry means disabled.
     *
     * @return whether users can register themselves in this instance
     */
    private boolean isRegistrationEnabled() {
        return registrationEnabled.isPresent() && registrationEnabled.get();
    }

    /**
     * Create user based on UserDTO. If the user management is done internally by Artemis, also create the user in the (optional) version control system
     * In case user management is done externally, the users groups are configured in the external user management as well.
     *
     * @param userDTO user data transfer object
     * @return newly created user
     */
    public User createUser(ManagedUserVM userDTO) {
        User user = new User();
        user.setLogin(userDTO.getLogin());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEmail(userDTO.getEmail());
        user.setImageUrl(userDTO.getImageUrl());
        if (userDTO.getLangKey() == null) {
            user.setLangKey(Constants.DEFAULT_LANGUAGE); // default language
        }
        else {
            user.setLangKey(userDTO.getLangKey());
        }

        setUserAuthorities(userDTO, user);

        if (userDTO.isInternal()) {
            String password = userDTO.getPassword() == null ? RandomUtil.generatePassword() : userDTO.getPassword();
            user.setPassword(passwordService.hashPassword(password));
        }
        user.setResetKey(RandomUtil.generateResetKey());
        user.setResetDate(Instant.now());
        try {
            Set<Organization> matchingOrganizations = organizationRepository.getAllMatchingOrganizationsByUserEmail(userDTO.getEmail());
            user.setOrganizations(matchingOrganizations);
        }
        catch (InvalidDataAccessApiUsageException | PatternSyntaxException pse) {
            log.warn("Could not retrieve matching organizations from pattern: {}", pse.getMessage());
        }
        user.setActivated(true);
        user.setInternal(userDTO.isInternal());
        user.setTestUser(userDTO.isTestUser());
        // an empty string is considered as null to satisfy the unique constraint on registration number
        if (StringUtils.hasText(userDTO.getVisibleRegistrationNumber())) {
            user.setRegistrationNumber(userDTO.getVisibleRegistrationNumber());
        }
        saveUser(user);

        log.debug("Created Information for User: {}", user);
        return user;
    }

    /**
     * Updates the authorities for the user according to the ones set in the DTO.
     *
     * @param userDTO The source for the authorities that should be set.
     * @param user    The target user where the authorities are set.
     */
    private void setUserAuthorities(final ManagedUserVM userDTO, final User user) {
        // A user needs to have at least some role, otherwise an authentication token can never be constructed
        if (userDTO.getAuthorities() == null || userDTO.getAuthorities().isEmpty()) {
            userDTO.setAuthorities(Set.of(STUDENT.getAuthority()));
        }

        // clear and add instead of new Set for Hibernate change tracking
        final Set<Authority> authorities = user.getAuthorities();
        authorities.clear();
        userDTO.getAuthorities().stream().map(authorityRepository::findById).flatMap(Optional::stream).forEach(authorities::add);
    }

    /**
     * Update basic information (first name, last name, email, language) for the current user.
     * This method is typically invoked by the user
     *
     * @param firstName first name of user
     * @param lastName  last name of user
     * @param email     email id of user
     * @param langKey   language key
     * @param imageUrl  image URL of user
     */
    public void updateBasicInformationOfCurrentUser(String firstName, String lastName, String email, String langKey, String imageUrl) {
        SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneByLogin).ifPresent(user -> {
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email.toLowerCase());
            user.setLangKey(langKey);
            if (imageUrl != null) {
                user.setImageUrl(imageUrl);
            }
            saveUser(user);
            log.info("Changed Information for User: {}", user);
        });
    }

    /**
     * Update all information for a specific user (including its password), and return the modified user.
     * This method is typically invoked by the admin user
     * <p>
     * The edit form can flip {@code activated} in either direction, so this reaches the same transitions as
     * {@link #activateUser(User)} and {@link #deactivateUser(User)} without going through them. A deactivation therefore
     * repeats what {@link #deactivateUser(User)} does around the flag: it is written to the audit log, and the credentials
     * that would otherwise keep working over git are revoked. An activation is not audited here, because the caller
     * follows an activating update with {@link UserService#activateUser(User)}, which records it.
     *
     * @param user           The user that should get updated
     * @param updatedUserDTO The DTO containing the to be updated values
     * @return updated user
     */
    @NonNull
    public User updateUser(@NonNull User user, ManagedUserVM updatedUserDTO) {
        user.setLogin(updatedUserDTO.getLogin().toLowerCase());
        user.setFirstName(updatedUserDTO.getFirstName());
        user.setLastName(updatedUserDTO.getLastName());
        user.setEmail(updatedUserDTO.getEmail().toLowerCase());

        // allow to remove the registration: an empty string is considered as null to satisfy the unique constraint on registration number
        if (!StringUtils.hasText(updatedUserDTO.getVisibleRegistrationNumber())) {
            user.setRegistrationNumber(null);
        }
        else {
            user.setRegistrationNumber(updatedUserDTO.getVisibleRegistrationNumber());
        }
        if (updatedUserDTO.getImageUrl() != null) {
            user.setImageUrl(updatedUserDTO.getImageUrl());
        }
        // Captured before the flag is overwritten: the admin edit form reaches the same transition as deactivateUser, and
        // it has to revoke the same credentials, otherwise an account the admin sees as deactivated keeps working over git.
        boolean isBeingDeactivated = Boolean.TRUE.equals(user.getActivated()) && !updatedUserDTO.isActivated();
        user.setActivated(updatedUserDTO.isActivated());
        user.setTestUser(updatedUserDTO.isTestUser());
        user.setLangKey(updatedUserDTO.getLangKey());

        // if user was external and becomes internal - it's important to make sure that user still has a password
        boolean wasInternal = user.isInternal();
        user.setInternal(updatedUserDTO.isInternal());
        boolean revokeCredentialsAfterPasswordChange = user.isInternal() && updatedUserDTO.getPassword() != null && updatedUserDTO.isRevokeCredentials();

        if (user.isInternal()) {
            if (updatedUserDTO.getPassword() != null) {
                user.setPassword(passwordService.hashPassword(updatedUserDTO.getPassword()));
            }
            else if (!wasInternal || user.getPassword() == null) {
                // If user becomes internal user and got no password, generate the random password
                String newPassword = RandomUtil.generatePassword();
                user.setPassword(passwordService.hashPassword(newPassword));
            }
        }
        user.setOrganizations(updatedUserDTO.getOrganizations());
        setUserAuthorities(updatedUserDTO, user);

        log.debug("Changed Information for User: {}", user);

        User savedUser = saveUser(user);
        // Only the deactivation is audited here. The admin edit form reaches that transition without going through
        // deactivateUser, so it would otherwise go unrecorded. The opposite direction needs no entry here: the only caller,
        // AdminUserResource.updateUser, follows an activating update with userService.activateUser, which audits it - doing
        // it in both places recorded a single activation twice.
        if (isBeingDeactivated) {
            auditAccountStateChange(savedUser, Constants.DEACTIVATE_USER);
        }
        boolean passwordChangedByAdministrator = user.isInternal() && updatedUserDTO.getPassword() != null;
        boolean credentialsRevoked = isBeingDeactivated || revokeCredentialsAfterPasswordChange;
        if (credentialsRevoked) {
            String reason = isBeingDeactivated ? "user deactivated by an administrator" : "password changed by an administrator";
            accountCredentialRevocationService.revokeAllCredentials(savedUser, reason);
        }
        if (passwordChangedByAdministrator) {
            // The affected user is told, not the administrator who did it: their credentials just stopped working, and only
            // this email lets them tell an administrator's action apart from an intruder's. The acting administrator is
            // recorded in the audit event instead. Deactivation alone is not announced here - the user cannot sign in to act
            // on it, and #13404 already blocks authentication for inactive accounts.
            //
            // Reported from what was actually revoked, not from the checkbox: deactivating and changing the password in one
            // update revokes everything through `isBeingDeactivated`, so keying the message off the checkbox alone told the
            // user their keys and tokens had been kept while they had in fact just been deleted.
            CredentialRevocationChoiceDTO revoked = credentialsRevoked ? new CredentialRevocationChoiceDTO(true, true, true) : CredentialRevocationChoiceDTO.none();
            accountSecurityNotificationService.passwordChanged(savedUser, revoked, AccountSecurityNotificationService.PasswordChangeActor.ADMINISTRATOR);
        }
        return savedUser;
    }

    /**
     * Activates an account, clears its activation key, and records the change in the audit log.
     * <p>
     * Reached both from the administrative activate endpoint and from a user redeeming their own activation key, so the
     * recorded principal is whichever of the two performed it.
     *
     * @param user the user that should be activated
     */
    public void activateUser(User user) {
        user.setActivated(true);
        user.setActivationKey(null);
        saveUser(user);
        auditAccountStateChange(user, Constants.ACTIVATE_USER);
        log.info("Activated user: {}", user);
    }

    /**
     * Deactivates an account so it can no longer authenticate anywhere, records the change in the audit log, and revokes the
     * credentials that would otherwise keep working without it.
     * <p>
     * Only an administrator can reverse this. No endpoint lets the account holder activate themselves again - see
     * {@link de.tum.cit.aet.artemis.account.web.UserResource#initializeUser()}, which deliberately does not touch the flag.
     *
     * @param user the user that should be deactivated
     */
    public void deactivateUser(User user) {
        user.setActivated(false);
        saveUser(user);
        auditAccountStateChange(user, Constants.DEACTIVATE_USER);
        // Web login checks `activated` on every attempt, but the git authentication paths accept a VCS access token or an
        // SSH key without consulting account state, so deactivation only takes effect once those credentials are gone.
        accountCredentialRevocationService.revokeAllCredentials(user, "user deactivated");
        log.info("Deactivated user: {}", user);
    }

    /**
     * Records a change to an account's {@code activated} state in the audit log.
     * <p>
     * Deactivating an account revokes access everywhere, so who did it and to whom has to be reconstructable long after
     * the fact - the audit table keeps deliberate actions like this one far longer than login records. The principal is
     * whoever performed the change, which is an administrator for both the deactivate endpoint and the admin edit form,
     * and {@code system} where there is no authenticated actor, as when a user redeems their own activation key.
     *
     * @param user      the account whose state changed
     * @param eventType {@link Constants#ACTIVATE_USER} or {@link Constants#DEACTIVATE_USER}
     */
    private void auditAccountStateChange(User user, String eventType) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM_ACCOUNT);
        auditEventRepository.add(new AuditEvent(actor, eventType, "user=" + user.getLogin()));
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
     * Generates a fresh password for an LTI-provisioned account and marks it as initialised, so the launch hands the password
     * over exactly once. The returned value is the only place the plain password exists - the stored one is hashed.
     * <p>
     * Writes {@link User#isLtiInitialized()} rather than {@code activated}, which it used to set: that both overloaded a flag
     * meaning only "may authenticate" and let a deactivated account re-enable itself through the initialisation endpoint.
     *
     * @param user the user to update
     * @return the newly created password, in plain text, for display to that user
     */
    public String setRandomPasswordAndReturn(User user) {
        String newPassword = RandomUtil.generatePassword();
        user.setPassword(passwordService.hashPassword(newPassword));
        // Records that the password has been handed over, so the launch offers the dialog only once. This used to set
        // `activated` instead, which both overloaded that flag and let a deactivated account re-enable itself here.
        user.setLtiInitialized(true);
        userRepository.save(user);
        return newPassword;
    }

}
