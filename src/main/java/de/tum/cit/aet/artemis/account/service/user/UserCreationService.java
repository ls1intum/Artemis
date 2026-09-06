package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.service.UserRecoveryKeyService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.exception.EmailAlreadyUsedException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserCreationService {

    private static final Logger log = LoggerFactory.getLogger(UserCreationService.class);

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final AuthorityRepository authorityRepository;

    private final OrganizationRepository organizationRepository;

    private final AccountCredentialRevocationService accountCredentialRevocationService;

    private final AccountSecurityNotificationService accountSecurityNotificationService;

    private final AuditEventRepository auditEventRepository;

    private final UserRecoveryKeyService userRecoveryKeyService;

    private final UserActivityService userActivityService;

    public UserCreationService(UserRepository userRepository, PasswordService passwordService, AuthorityRepository authorityRepository,
            OrganizationRepository organizationRepository, AccountCredentialRevocationService accountCredentialRevocationService,
            AccountSecurityNotificationService accountSecurityNotificationService, AuditEventRepository auditEventRepository, UserRecoveryKeyService userRecoveryKeyService,
            UserActivityService userActivityService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.authorityRepository = authorityRepository;
        this.organizationRepository = organizationRepository;
        this.accountCredentialRevocationService = accountCredentialRevocationService;
        this.accountSecurityNotificationService = accountSecurityNotificationService;
        this.auditEventRepository = auditEventRepository;
        this.userRecoveryKeyService = userRecoveryKeyService;
        this.userActivityService = userActivityService;
    }

    /**
     * Create user only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     * <p>
     * The account is created <b>activated</b> unless its own owner is expected to activate it, which requires
     * {@code isInternal}: only an internal account gets {@code activated = false} and an activation key. See
     * {@link User#activated} for why an externally managed account must never be created unactivated, and for why this is
     * deliberately not narrowed further to instances that have self-registration enabled.
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
        validateEmailIsAvailable(newUser.getEmail(), null);
        // an empty string is considered as null to satisfy the unique constraint on registration number
        if (StringUtils.hasText(registrationNumber)) {
            newUser.setRegistrationNumber(registrationNumber);
        }
        newUser.setImageUrl(imageUrl);
        newUser.setLangKey(langKey);
        // An externally managed account is created activated. The activation key is redeemable exclusively through GET /activate, so an
        // account that authenticates against an external directory never receives one and could never redeem it; creating such an account
        // unactivated would leave behind an account that nothing can ever activate, which the git authentication paths then refuse.
        //
        // Narrowing this further - to internal accounts on an instance that actually has self-registration enabled - is a separate change:
        // the LTI launch also creates an internal account here, and although it decides initialisation on user_lti.initialized rather than
        // on `activated`, it does rely on the account starting out inactive.
        if (isInternal) {
            newUser.setActivated(false);
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
            Set<Organization> matchingOrganizations = organizationRepository.getAllMatchingOrganizationsByUserEmail(newUser.getEmail());
            newUser.setOrganizations(matchingOrganizations);
        }
        catch (InvalidDataAccessApiUsageException | PatternSyntaxException pse) {
            log.warn("Could not retrieve matching organizations from pattern: {}", pse.getMessage());
        }
        newUser = saveUser(newUser);
        if (isInternal) {
            // Stored after the save, since the key is keyed on the user id.
            userRecoveryKeyService.storeActivationKey(newUser.getId(), RandomUtil.generateActivationKey());
        }
        log.debug("Created user: {}", newUser);
        return newUser;
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
        validateEmailIsAvailable(user.getEmail(), null);
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
        try {
            Set<Organization> matchingOrganizations = organizationRepository.getAllMatchingOrganizationsByUserEmail(user.getEmail());
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
        // An administrator-created account gets a reset key so its owner can set their own password.
        userRecoveryKeyService.storeResetKey(user.getId(), RandomUtil.generateResetKey(), Instant.now());

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
            updateEmailIfChanged(user, email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
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
        updateEmailIfChanged(user, updatedUserDTO.getEmail());
        user.setLogin(updatedUserDTO.getLogin().toLowerCase(Locale.ENGLISH));
        user.setFirstName(updatedUserDTO.getFirstName());
        user.setLastName(updatedUserDTO.getLastName());

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
        // Captured before the flag is overwritten: the admin edit form reaches the same two transitions as deactivateUser
        // and a password reset do. A session established earlier has to stop being extended for both, and the credentials
        // have to be revoked as well, otherwise an account the admin sees as deactivated keeps working over git.
        boolean isBeingDeactivated = Boolean.TRUE.equals(user.getActivated()) && !updatedUserDTO.isActivated();
        user.setActivated(updatedUserDTO.isActivated());
        user.setTestUser(updatedUserDTO.isTestUser());
        user.setLangKey(updatedUserDTO.getLangKey());

        // if user was external and becomes internal - it's important to make sure that user still has a password
        boolean wasInternal = user.isInternal();
        user.setInternal(updatedUserDTO.isInternal());

        // Set where the password is actually written rather than derived from the request, because only some requests that
        // carry a password write it: an update that leaves the account external ignores it, and the changed date has to
        // follow what happened to the credential, not what was asked for.
        boolean isPasswordBeingChanged = false;
        if (user.isInternal()) {
            if (updatedUserDTO.getPassword() != null) {
                user.setPassword(passwordService.hashPassword(updatedUserDTO.getPassword()));
                isPasswordBeingChanged = true;
            }
            else if (!wasInternal || user.getPassword() == null) {
                // If user becomes internal user and got no password, generate the random password
                String newPassword = RandomUtil.generatePassword();
                user.setPassword(passwordService.hashPassword(newPassword));
                // Deliberately not treated as a password change: the account had no usable password before, so there is no
                // earlier password-based session for the changed date to end.
            }
        }
        // Bumping the changed date always stops an earlier session from being extended; revoking the other credentials on
        // top of that stays opt-in, because the admin form asks for it separately.
        boolean revokeCredentialsAfterPasswordChange = isPasswordBeingChanged && updatedUserDTO.isRevokeCredentials();
        boolean credentialsChanged = isBeingDeactivated || isPasswordBeingChanged;
        user.setOrganizations(updatedUserDTO.getOrganizations());
        setUserAuthorities(updatedUserDTO, user);

        log.debug("Changed Information for User: {}", user);

        User savedUser = saveUser(user);
        if (credentialsChanged) {
            // Stops sessions established before this change from being extended any further. Stamped after the save so it
            // is keyed on a persisted id, and outside the entity so the timestamp is not carried on every user load.
            userActivityService.recordCredentialsChanged(savedUser.getId(), Instant.now());
        }
        // Same condition as the changed date above, so the notice cannot claim a change the account did not receive.
        boolean passwordChangedByAdministrator = isPasswordBeingChanged;
        boolean credentialsRevoked = isBeingDeactivated || revokeCredentialsAfterPasswordChange;
        if (credentialsRevoked) {
            String reason = isBeingDeactivated ? "user deactivated by an administrator" : "password changed by an administrator";
            accountCredentialRevocationService.revokeAllCredentials(savedUser, reason);
        }
        // Only the deactivation is audited here, and only once the revocation it implies has run, so the entry describes a
        // transition that has actually taken effect. The admin edit form reaches that transition without going through
        // deactivateUser, so it would otherwise go unrecorded. The opposite direction needs no entry here: the only caller,
        // AdminUserResource.updateUser, follows an activating update with userService.activateUser, which audits it - doing
        // it in both places recorded a single activation twice.
        if (isBeingDeactivated) {
            // Same reason as in deactivateUser: an outstanding activation or reset key would be a way back into an account
            // whose control has just been taken away. Deliberately not done for a plain administrative password change,
            // which revokes credentials too but must leave an administrator-created account's invitation keys intact.
            userRecoveryKeyService.clearAll(savedUser.getId());
            auditAccountStateChange(savedUser, Constants.DEACTIVATE_USER);
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
        saveUser(user);
        userRecoveryKeyService.clearActivationKey(user.getId());
        auditAccountStateChange(user, Constants.ACTIVATE_USER);
        log.info("Activated user: {}", user);
    }

    /**
     * Deactivates an account so it can no longer authenticate anywhere, records the change in the audit log, and revokes the
     * credentials that would otherwise keep working without it.
     * <p>
     * Only an administrator can reverse this. No endpoint lets the account holder activate themselves again: the one that
     * sets the flag, {@link de.tum.cit.aet.artemis.account.web.UserResource#initializeUser()}, decides whether it may run
     * from the lti module's own initialisation marker rather than from this flag, and a deactivated account has that marker
     * set already.
     *
     * @param user the user that should be deactivated
     */
    public void deactivateUser(User user) {
        user.setActivated(false);
        saveUser(user);
        // Stops sessions established before the deactivation from being extended any further.
        userActivityService.recordCredentialsChanged(user.getId(), Instant.now());
        // Web login checks `activated` on every attempt, but the git authentication paths accept a VCS access token or an
        // SSH key without consulting account state, so deactivation only takes effect once those credentials are gone.
        // Done before the audit entry so that a failure while writing the entry cannot leave an account flagged as
        // deactivated while its tokens and keys still work.
        accountCredentialRevocationService.revokeAllCredentials(user, "user deactivated");
        // An outstanding activation key would be a way to undo this: an account still awaiting activation would otherwise
        // keep a working link that flips `activated` back on. A pending reset key goes for the same reason. Done before the
        // audit entry for the same reason the revocation is.
        userRecoveryKeyService.clearAll(user.getId());
        auditAccountStateChange(user, Constants.DEACTIVATE_USER);
        log.info("Deactivated user: {}", user);
    }

    /**
     * Records a change to an account's {@code activated} state in the audit log.
     * <p>
     * Deactivating an account revokes access everywhere, so who did it and to whom has to be reconstructable long after
     * the fact - the audit table keeps deliberate actions like this one far longer than login records. The principal is
     * whoever performed the change, which is an administrator for both the deactivate endpoint and the admin edit form,
     * and {@code system} where there is no authenticated actor, as when a user redeems their own activation key.
     * <p>
     * Best-effort with respect to the caller, like {@code AccountSecurityEventService}: the state change and the
     * credential revocation that accompanies it have already happened by the time this runs, so letting a failed audit
     * write propagate would report a deactivation as failed after it had taken effect. A failure is logged at error level
     * instead.
     *
     * @param user      the account whose state changed
     * @param eventType {@link Constants#ACTIVATE_USER} or {@link Constants#DEACTIVATE_USER}
     */
    private void auditAccountStateChange(User user, String eventType) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM_ACCOUNT);
        try {
            auditEventRepository.add(new AuditEvent(actor, eventType, "user=" + user.getLogin()));
        }
        catch (Exception e) {
            log.error("Could not record audit event {} for user {} performed by {}", eventType, user.getLogin(), actor, e);
        }
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
     * Rejects a non-blank email address that belongs to another account. Existing legacy duplicates remain editable as long as their email address is not changed through a
     * path that invokes this validation. The database constraint introduced in the implementation phase will close the concurrent-write race that application validation
     * cannot eliminate.
     *
     * @param email         the proposed email address
     * @param currentUserId the current account when updating, or {@code null} when creating an account
     * @throws EmailAlreadyUsedException if another account already uses the address, ignoring case
     */
    public void validateEmailIsAvailable(@Nullable String email, @Nullable Long currentUserId) {
        String canonicalEmail = User.canonicalEmail(email);
        if (canonicalEmail == null) {
            return;
        }

        boolean emailAlreadyUsed = currentUserId == null ? userRepository.existsByEmailIgnoreCase(canonicalEmail)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(canonicalEmail, currentUserId);
        if (emailAlreadyUsed) {
            throw new EmailAlreadyUsedException();
        }
    }

    /**
     * Applies an email update only when its canonical value differs from the account's current canonical value. This
     * keeps unrelated edits possible for accounts in a legacy duplicate group and prevents case-only values from external
     * systems from causing repeated validation and saves.
     *
     * @param user  the account to update
     * @param email the proposed address, which may be {@code null} or blank
     * @return whether the canonical email value changed
     * @throws EmailAlreadyUsedException if the changed non-blank address belongs to another account
     */
    public boolean updateEmailIfChanged(User user, @Nullable String email) {
        String currentEmail = User.canonicalEmail(user.getEmail());
        String updatedEmail = User.canonicalEmail(email);
        if (Objects.equals(currentEmail, updatedEmail)) {
            return false;
        }

        validateEmailIsAvailable(updatedEmail, user.getId());
        user.setEmail(updatedEmail);
        return true;
    }

    /**
     * Gives the account the password it authenticates with after its first LTI launch, and makes it usable.
     * <p>
     * The caller must already have claimed the initialisation, which is what guarantees this happens once. Written as one
     * guarded statement rather than by saving the entity the caller read, so that a deactivation or soft delete arriving
     * in between is not written back out.
     *
     * @param user the account being initialised
     * @return the new password, or empty if the account no longer exists to be initialised
     */
    public Optional<String> storeInitialPasswordAndActivate(User user) {
        String newPassword = RandomUtil.generatePassword();
        if (userRepository.storeInitialPasswordAndActivate(user.getId(), passwordService.hashPassword(newPassword)) != 1) {
            return Optional.empty();
        }
        // Same as activateUser: an activated account must not carry an activation key. The launch already discards the one
        // the factory issued, so this is belt and braces - but it keeps the invariant true of every write that activates.
        userRecoveryKeyService.clearActivationKey(user.getId());
        return Optional.of(newPassword);
    }

}
