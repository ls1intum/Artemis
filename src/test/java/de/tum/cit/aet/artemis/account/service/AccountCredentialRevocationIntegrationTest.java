package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.PasskeyCredentialCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.account.util.PasskeyCredentialUtilService;
import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.admin.repository.SecurityAuditEventRepository;
import de.tum.cit.aet.artemis.admin.service.AuditEventService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.audit.AuditLogType;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.localvc.service.UserVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.domain.ParticipationVCSAccessToken;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryVCSAccessToken;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies against a real database that the account lifecycle transitions actually revoke the credentials that can be
 * used instead of the password.
 * <p>
 * These are the cases where a silent gap is expensive: a user who resets their password because they suspect a
 * compromise, and an administrator who deactivates an account. Each transition is asserted per credential type - the
 * passkeys, the personal VCS access token together with its expiry date, and the SSH keys - because the failure mode is
 * one type being forgotten. The fixture therefore contains every persisted credential category covered by the service:
 * passkeys, SSH keys, and personal, participation-scoped, and repository-scoped VCS access tokens.
 */
class AccountCredentialRevocationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "credentialrevocation";

    private static final Instant RESET_ISSUED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @Autowired
    private AccountCredentialRevocationService accountCredentialRevocationService;

    @Autowired
    private UserService userService;

    @Autowired
    private SecurityAuditEventRepository securityAuditEventRepository;

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private UserVcsAccessTokenService userVcsAccessTokenService;

    @Autowired
    private UserRecoveryKeyService userRecoveryKeyService;

    @Autowired
    private UserCreationService userCreationService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSshPublicKeyRepository userSshPublicKeyRepository;

    @Autowired
    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Autowired
    private PasskeyCredentialCleanupRepository passkeyCredentialCleanupRepository;

    @Autowired
    private PasskeyCredentialUtilService passkeyCredentialUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    @Autowired
    private RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    private User user;

    private ZonedDateTime vcsAccessTokenExpiryDate;

    private ZonedDateTime sshKeyCreationDate;

    private String passkeyCredentialId;

    private long participationId;

    private String repositoryUri;

    /**
     * The cleanup repository must delete passkeys through its own query, and must never be feature-gated.
     * <p>
     * Both halves matter. The query is exercised so that a malformed JPQL statement fails here: Hibernate validates
     * repository queries lazily, so a broken one otherwise survives mocked unit tests and only surfaces at startup.
     * The annotation check guards the actual defect this repository exists for - the previous implementation went
     * through {@code Optional<PasskeyCredentialsRepository>}, which is {@code @Conditional(PasskeyEnabled.class)} and
     * therefore absent while passkeys are disabled, so revocation silently left the rows in place and they became
     * usable again on re-enable. Asserting the absence of the annotation is what keeps that from being reintroduced;
     * booting a second Spring context with the feature off would cost far more and prove no more.
     */
    @Test
    void shouldDeletePasskeysUnconditionally() {
        assertThat(PasskeyCredentialCleanupRepository.class.getAnnotation(Conditional.class))
                .as("the cleanup repository must not be feature-gated, otherwise revocation is a no-op while passkeys are disabled").isNull();

        passkeyCredentialCleanupRepository.deleteAllByUserId(user.getId());
        var credentialId = passkeyCredentialUtilService.createAndSavePasskeyCredential(user).getCredentialId();
        assertThat(passkeyCredentialsRepository.findByCredentialId(credentialId)).isPresent();

        var deleted = passkeyCredentialCleanupRepository.deleteAllByUserId(user.getId());

        assertThat(deleted).isEqualTo(1);
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();
    }

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        user = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
    }

    /**
     * Gives the account one of each persisted credential category this service revokes.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void revokingThroughTheEndpointRemovesOnlyTheSelectedTypes() throws Exception {
        giveUserCredentials();

        request.postWithoutLocation("/api/account/revoke-credentials", new CredentialRevocationChoiceDTO(false, true, false), HttpStatus.OK, null);

        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertVcsAccessTokensKept();
        assertPasskeyKept();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void anExternalUserCanRevokeTheirOwnCredentials() throws Exception {
        // The reason this endpoint exists. An external user cannot change their password here at all, so the revocation
        // offered alongside a password change is unreachable for them and this is their only route to it.
        giveUserCredentials();
        user.setInternal(false);
        userRepository.save(user);

        request.postWithoutLocation("/api/account/revoke-credentials", new CredentialRevocationChoiceDTO(true, true, true), HttpStatus.OK, null);

        assertAllCredentialsRevoked();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void revokingNothingIsRejected() throws Exception {
        // A request that selects nothing is a client defect rather than a no-op worth accepting silently.
        giveUserCredentials();

        request.postWithoutLocation("/api/account/revoke-credentials", CredentialRevocationChoiceDTO.none(), HttpStatus.BAD_REQUEST, null);

        assertAllCredentialsKept();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void revokingThroughTheEndpointIsRecordedForAdministrators() throws Exception {
        giveUserCredentials();
        securityAuditEventRepository.deleteAll();

        request.postWithoutLocation("/api/account/revoke-credentials", new CredentialRevocationChoiceDTO(true, false, false), HttpStatus.OK, null);

        // The audit event is how an administrator reconstructs afterwards that the owner did this to their own account.
        // Only the type and the principal are asserted here: `data` is a lazy element collection that cannot be read
        // outside a session, and AccountSecurityNotificationServiceTest already pins its contents exactly.
        assertThat(securityAuditEventRepository.findAll()).anySatisfy(event -> {
            assertThat(event.getAuditEventType()).isEqualTo(Constants.REVOKE_OWN_CREDENTIALS);
            assertThat(event.getPrincipal()).isEqualTo(user.getLogin());
        });
    }

    /**
     * Deactivation takes control of the account away, so it must not leave a way back in. An account still awaiting
     * self-activation holds an activation key, and that key flips {@code activated} back on when redeemed - so the keys go
     * with the other credentials.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void deactivatingAnAccountDropsItsOutstandingRecoveryKeys() {
        userRecoveryKeyService.storeActivationKey(user.getId(), "activation-key-1");
        userRecoveryKeyService.storeResetKey(user.getId(), "reset-key-1", RESET_ISSUED_AT);

        userCreationService.deactivateUser(user);

        assertThat(userRecoveryKeyService.findActivationKey(user.getId())).isNull();
        assertThat(userRecoveryKeyService.findResetKey(user.getId())).isNull();
        // And the key can no longer be redeemed, which is the point of clearing it.
        assertThat(userRecoveryKeyService.findUserIdByActivationKey("activation-key-1")).isEmpty();
    }

    /**
     * A password change made by an administrator revokes credentials too, but an administrator-created account is handed
     * its activation and reset keys precisely so its owner can get in for the first time. Those must survive.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void anAdministrativePasswordChangeKeepsTheInvitationKeys() {
        userRecoveryKeyService.storeActivationKey(user.getId(), "activation-key-2");
        userRecoveryKeyService.storeResetKey(user.getId(), "reset-key-2", RESET_ISSUED_AT);

        accountCredentialRevocationService.revokeAllCredentials(user, "password changed by an administrator");

        assertThat(userRecoveryKeyService.findActivationKey(user.getId())).isEqualTo("activation-key-2");
        assertThat(userRecoveryKeyService.findResetKey(user.getId())).isEqualTo("reset-key-2");
    }

    /**
     * Presenting no key at all must match nothing. A derived query turns a null argument into {@code IS NULL}, which
     * would otherwise match the row of an account that holds only the other key.
     */
    @Test
    void anAbsentKeyMatchesNothing() {
        userRecoveryKeyService.storeActivationKey(user.getId(), "activation-key-3");

        assertThat(userRecoveryKeyService.findByResetKey(null)).isEmpty();
        assertThat(userRecoveryKeyService.findByResetKey("")).isEmpty();
        assertThat(userRecoveryKeyService.findUserIdByActivationKey(null)).isEmpty();
        assertThat(userRecoveryKeyService.findUserIdByActivationKey("  ")).isEmpty();
    }

    private void giveUserCredentials() {
        // Cleared first: the fixture user is reused across the tests in this class, so a test that deliberately leaves a
        // credential in place would otherwise make a later test see two of them.
        passkeyCredentialCleanupRepository.deleteAllByUserId(user.getId());
        userSshPublicKeyRepository.deleteAll(userSshPublicKeyRepository.findAllByUserId(user.getId()));
        participationVCSAccessTokenRepository.deleteAllByUserId(user.getId());
        repositoryVCSAccessTokenRepository.deleteAllByUserId(user.getId());

        passkeyCredentialId = passkeyCredentialUtilService.createAndSavePasskeyCredential(user).getCredentialId();

        vcsAccessTokenExpiryDate = ZonedDateTime.now().plusMonths(6).withNano(0);
        // Seeded through the service: the personal token lives in user_vcs_access_token, not on the user row.
        userVcsAccessTokenService.store(user.getId(), "vcs-token-" + user.getId(), vcsAccessTokenExpiryDate);

        UserSshPublicKey sshKey = new UserSshPublicKey();
        sshKey.setUserId(user.getId());
        sshKey.setLabel("Test key");
        sshKey.setPublicKey("ssh-ed25519 AAAA-not-a-real-key-" + user.getId());
        sshKey.setKeyHash("hash-" + user.getId());
        sshKeyCreationDate = ZonedDateTime.now().withNano(0);
        sshKey.setCreationDate(sshKeyCreationDate);
        userSshPublicKeyRepository.save(sshKey);

        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, "Credential Revocation", "CRREV");
        ProgrammingExercise exercise = course.getExercises().stream().filter(ProgrammingExercise.class::isInstance).map(ProgrammingExercise.class::cast).findFirst().orElseThrow();
        var participation = ParticipationFactory.generateIndividualProgrammingExerciseStudentParticipation(exercise, user);
        participation.setRepositoryUri("http://localhost/git/CRREV/" + user.getLogin() + ".git");
        participation = programmingExerciseStudentParticipationRepository.save(participation);
        participationId = participation.getId();

        ParticipationVCSAccessToken participationToken = new ParticipationVCSAccessToken();
        participationToken.setUser(user);
        participationToken.setParticipation(participation);
        participationToken.setVcsAccessToken("participation-token-" + user.getId());
        participationVCSAccessTokenRepository.save(participationToken);

        repositoryUri = "http://localhost/git/CRREV/crrev-template.git";
        RepositoryVCSAccessToken repositoryToken = new RepositoryVCSAccessToken();
        repositoryToken.setUser(user);
        repositoryToken.setExercise(exercise);
        repositoryToken.setRepositoryType(RepositoryType.TEMPLATE);
        repositoryToken.setRepositoryUri(repositoryUri);
        repositoryToken.setVcsAccessToken("repository-token-" + user.getId());
        repositoryVCSAccessTokenRepository.save(repositoryToken);
    }

    private User reloadUser() {
        return userRepository.getUserByLoginElseThrow(user.getLogin());
    }

    /**
     * Asserts that the personal VCS access token is gone, expiry date included: clearing only the token would leave a
     * dangling expiry date, and a later change that forgot the token itself would still look like a revocation.
     */
    private void assertVcsAccessTokensRevoked() {
        assertVcsAccessTokensRevoked(reloadUser());
    }

    /**
     * @param reloaded the account as it was read back from the database; a soft-deleted account has to be loaded by id,
     *                     because it can no longer be looked up by login
     */
    private void assertVcsAccessTokensRevoked(User reloaded) {
        // Revocation deletes the row, so "no token" is the absence of a row rather than nulled columns.
        assertThat(userVcsAccessTokenService.findToken(reloaded.getId())).isNull();
        assertThat(userVcsAccessTokenService.findExpiryDate(reloaded.getId())).isNull();
        assertThat(participationVCSAccessTokenRepository.findOverviewsByUserId(user.getId())).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findOverviewsByUserId(user.getId())).isEmpty();
    }

    private void assertVcsAccessTokensKept() {
        User reloaded = reloadUser();
        assertThat(userVcsAccessTokenService.findToken(reloaded.getId())).isEqualTo("vcs-token-" + user.getId());
        assertThat(userVcsAccessTokenService.findExpiryDate(reloaded.getId())).isNotNull();
        assertThat(participationVCSAccessTokenRepository.findByUserIdAndParticipationId(user.getId(), participationId)).hasValueSatisfying(token -> {
            assertThat(token.getVcsAccessToken()).isEqualTo("participation-token-" + user.getId());
            assertThat(token.getUser().getId()).isEqualTo(user.getId());
            assertThat(token.getParticipation().getId()).isEqualTo(participationId);
        });
        assertThat(participationVCSAccessTokenRepository.findOverviewsByUserId(user.getId())).hasSize(1);
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), repositoryUri)).hasValueSatisfying(token -> {
            assertThat(token.getVcsAccessToken()).isEqualTo("repository-token-" + user.getId());
            assertThat(token.getRepositoryType()).isEqualTo(RepositoryType.TEMPLATE);
            assertThat(token.getRepositoryUri()).isEqualTo(repositoryUri);
        });
        assertThat(repositoryVCSAccessTokenRepository.findOverviewsByUserId(user.getId())).hasSize(1);
    }

    private void assertSshKeyKept() {
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).singleElement().satisfies(sshKey -> {
            assertThat(sshKey.getUserId()).isEqualTo(user.getId());
            assertThat(sshKey.getLabel()).isEqualTo("Test key");
            assertThat(sshKey.getPublicKey()).isEqualTo("ssh-ed25519 AAAA-not-a-real-key-" + user.getId());
            assertThat(sshKey.getKeyHash()).isEqualTo("hash-" + user.getId());
            assertThat(sshKey.getCreationDate()).isEqualTo(sshKeyCreationDate);
        });
    }

    private void assertPasskeyKept() {
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).singleElement().satisfies(passkey -> {
            assertThat(passkey.getCredentialId()).isEqualTo(passkeyCredentialId);
            assertThat(passkey.getLabel()).isEqualTo("Default Passkey Label");
            assertThat(passkey.getUser().getId()).isEqualTo(user.getId());
            assertThat(passkey.getUvInitialized()).isTrue();
            assertThat(passkey.getBackupEligible()).isTrue();
            assertThat(passkey.getBackupState()).isTrue();
        });
    }

    private void assertAllCredentialsKept() {
        assertVcsAccessTokensKept();
        assertSshKeyKept();
        assertPasskeyKept();
    }

    private void assertAllCredentialsRevoked() {
        assertAllCredentialsRevoked(reloadUser());
    }

    private void assertAllCredentialsRevoked(User reloaded) {
        assertVcsAccessTokensRevoked(reloaded);
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();
    }

    private void assertPasswordChangedTo(String previousPasswordHash, String newPassword) {
        User reloaded = reloadUser();
        assertThat(reloaded.getPassword()).isNotEqualTo(previousPasswordHash);
        assertThat(passwordService.checkPasswordMatch(newPassword, reloaded.getPassword())).isTrue();
        assertThat(reloaded.getActivated()).isTrue();
        assertThat(reloaded.isInternal()).isTrue();
        assertThat(reloaded.isDeleted()).isFalse();
    }

    @Test
    void revokeAllCredentialsRemovesEveryCredentialType() {
        giveUserCredentials();

        accountCredentialRevocationService.revokeAllCredentials(user, "test");

        assertAllCredentialsRevoked();
    }

    @Test
    void aCompletedPasswordResetRevokesEverythingByDefault() {
        // The remediation a user is told to perform has to end the intrusion, not just change one of several credentials,
        // so revoking everything is what a reset does unless the user says otherwise.
        giveUserCredentials();
        prepareResetKey();

        userService.completePasswordReset("new-Password-123", "reset-key-" + user.getId(), new CredentialRevocationChoiceDTO(true, true, true)).orElseThrow();

        assertAllCredentialsRevoked();
    }

    @Test
    void aCompletedPasswordResetKeepsTheCredentialsTheUserChoseToKeep() {
        // Forgetting a password is not the same as losing it to someone else. Re-enrolling every authenticator and key is
        // a real cost, so the user can keep them - and the choice has to be honoured rather than overridden by the reset.
        giveUserCredentials();
        prepareResetKey();

        userService.completePasswordReset("new-Password-123", "reset-key-" + user.getId(), CredentialRevocationChoiceDTO.none()).orElseThrow();

        assertAllCredentialsKept();
    }

    @Test
    void aCompletedPasswordResetRevokesOnlyTheSelectedCredentialType() {
        // A partial choice must be applied exactly: revoking SSH keys must not take the passkeys with it.
        giveUserCredentials();
        prepareResetKey();

        userService.completePasswordReset("new-Password-123", "reset-key-" + user.getId(), new CredentialRevocationChoiceDTO(false, true, false)).orElseThrow();

        assertPasskeyKept();
        assertVcsAccessTokensKept();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
    }

    private void prepareResetKey() {
        // Deliberately clock-relative, unlike the fixed dates elsewhere in this class: completePasswordReset only accepts a
        // key issued within the last 24 hours, so a fixed date would expire and the reset would be refused.
        userRecoveryKeyService.storeResetKey(user.getId(), "reset-key-" + user.getId(), Instant.now());
        userRepository.save(user);
    }

    @Test
    void deactivatingAUserRevokesEverything() {
        // Web login rejects a deactivated user, but the git paths accept a token or an SSH key without checking account
        // state, so deactivation is only effective once those are gone.
        giveUserCredentials();

        userCreationService.deactivateUser(user);

        assertThat(reloadUser().getActivated()).isFalse();
        assertAllCredentialsRevoked();
    }

    /**
     * Deactivation cuts off every form of access, so the log has to say who did it and to whom. Asserted for both routes
     * that write the flag, because they are separate code paths: the dedicated endpoint calls deactivateUser, while the
     * admin edit form writes it inside updateUser.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deactivatingAUserIsRecordedInTheAuditLog() {
        securityAuditEventRepository.deleteAll();

        userCreationService.deactivateUser(user);

        assertAccountStateAudited(Constants.DEACTIVATE_USER);
        assertThat(deactivationEvents()).as("one deactivation produces one audit entry").hasSize(1);
    }

    /**
     * Once, not once per thing the deactivation does. {@code updateUser} both revokes the credentials and audits, and an
     * entry written on either side of the revocation reads the same in the log - so only counting catches the case where
     * both happen.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deactivatingAUserThroughTheAdminUpdateIsRecordedExactlyOnce() {
        securityAuditEventRepository.deleteAll();

        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities);
        update.setActivated(false);
        update.setPassword(null);
        userCreationService.updateUser(userWithAuthorities, update);

        assertAccountStateAudited(Constants.DEACTIVATE_USER);
        assertThat(deactivationEvents()).as("one deactivation produces one audit entry").hasSize(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void activatingAUserIsRecordedInTheAuditLog() {
        userCreationService.deactivateUser(user);
        securityAuditEventRepository.deleteAll();

        userCreationService.activateUser(reloadUser());

        assertAccountStateAudited(Constants.ACTIVATE_USER);
    }

    /**
     * The admin edit form and the dedicated activate action both run for one activation through the resource, so the
     * transition has to be recorded once rather than by each of them.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void activatingThroughTheAdminUpdateIsRecordedExactlyOnce() {
        userCreationService.deactivateUser(user);
        securityAuditEventRepository.deleteAll();

        // Mirrors AdminUserResource.updateUser, which follows an activating update with userService.activateUser.
        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities);
        update.setActivated(true);
        update.setPassword(null);
        User updated = userCreationService.updateUser(userWithAuthorities, update);
        userService.activateUser(updated);

        assertThat(auditEventService.findAll(AuditLogType.SECURITY, Pageable.unpaged()).stream().filter(event -> Constants.ACTIVATE_USER.equals(event.getType())).toList())
                .as("one activation produces one audit entry").hasSize(1);
    }

    private List<AuditEvent> deactivationEvents() {
        return auditEventService.findAll(AuditLogType.SECURITY, Pageable.unpaged()).stream().filter(event -> Constants.DEACTIVATE_USER.equals(event.getType())).toList();
    }

    /**
     * Read through AuditEventService rather than the repository, because it loads {@code data} through an entity graph
     * while the repository's findAll() leaves that collection lazy and unreadable outside a session. The log to read from
     * is the security one: an account-state change is a change to what the account can authenticate with, and a
     * deactivation is the only record of the credential revocation it performs (see
     * {@code AuditEventConstants.SECURITY_EVENT_TYPES}).
     */
    private void assertAccountStateAudited(String expectedType) {
        assertThat(auditEventService.findAll(AuditLogType.SECURITY, Pageable.unpaged())).anySatisfy(event -> {
            assertThat(event.getType()).isEqualTo(expectedType);
            assertThat(event.getPrincipal()).as("the administrator who performed it, not the affected account").isEqualTo("admin");
            assertThat(event.getData()).containsEntry("user", user.getLogin());
        });
    }

    @Test
    void softDeletingAUserRevokesEverything() {
        // The pre-existing cleanup here deleted the SSH keys but left the personal VCS access token behind, so a
        // soft-deleted account kept working over git.
        giveUserCredentials();

        userService.softDeleteUser(user.getLogin());

        User deleted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertAllCredentialsRevoked(deleted);
    }

    /**
     * Goes through {@link UserService#changePassword} rather than the revocation service directly, because the wiring is
     * the part that can break: a change that dropped the revocation call from the password-change flow would still leave
     * a direct call to the service passing.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void aPasswordChangeRevokesOnlyWhatTheUserSelected() {
        // The user decides, because only they know whether the old password may have been seen by someone else. Selecting
        // the tokens must not take away the SSH keys they use from their machines.
        giveUserCredentials();

        userService.changePassword(UserFactory.USER_PASSWORD, "new-Password-123", new CredentialRevocationChoiceDTO(false, false, true));

        assertVcsAccessTokensRevoked();
        assertSshKeyKept();
        assertPasskeyKept();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void aPasswordChangeCanRevokeOnlyTheSshKeys() {
        giveUserCredentials();

        userService.changePassword(UserFactory.USER_PASSWORD, "new-Password-123", new CredentialRevocationChoiceDTO(false, true, false));

        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertVcsAccessTokensKept();
        assertPasskeyKept();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void aPasswordChangeCanRevokeOnlyThePasskeys() {
        giveUserCredentials();

        userService.changePassword(UserFactory.USER_PASSWORD, "new-Password-123", new CredentialRevocationChoiceDTO(true, false, false));

        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();
        assertVcsAccessTokensKept();
        assertSshKeyKept();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void aRoutinePasswordChangeRevokesNothing() {
        // The default when the request expresses no choice: a routine rotation should not cost the user their
        // authenticators, keys and tokens.
        giveUserCredentials();

        userService.changePassword(UserFactory.USER_PASSWORD, "new-Password-123", CredentialRevocationChoiceDTO.none());

        assertAllCredentialsKept();
    }

    /**
     * Deactivation through the admin edit form has to revoke the same credentials as the dedicated deactivate endpoint:
     * {@code updateUser} writes the {@code activated} flag itself, so without this an account the administrator sees as
     * deactivated keeps working over git and keeps its passkeys.
     */
    @Test
    void deactivatingAUserThroughTheAdminUpdateRevokesEverything() {
        giveUserCredentials();

        // Loaded with authorities, because updateUser reconciles them and the admin resource loads the user the same way.
        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities);
        update.setActivated(false);
        update.setPassword(null);
        userCreationService.updateUser(userWithAuthorities, update);

        assertThat(reloadUser().getActivated()).isFalse();
        assertAllCredentialsRevoked();
    }

    /**
     * Regression test: deactivating and changing the password in one update revokes everything through the deactivation
     * branch, so the notification has to say so. Keying the message off the revoke-credentials checkbox alone told the
     * user their keys and tokens had been kept while they had in fact just been deleted.
     */
    @Test
    void deactivatingWhileChangingThePasswordReportsThatEverythingWasRevoked() {
        giveUserCredentials();
        securityAuditEventRepository.deleteAll();

        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities);
        update.setActivated(false);
        update.setPassword("new-Password-123");
        // Deliberately not selected: the deactivation is what revokes here, and the report must follow the effect rather
        // than the checkbox.
        update.setRevokeCredentials(false);
        userCreationService.updateUser(userWithAuthorities, update);

        assertAllCredentialsRevoked();
        // Read through AuditEventService: it loads `data` through an entity graph, while findAll() leaves that collection
        // lazy and unreadable outside a session. The log to read from is the security one, because a password change is
        // a credential change (see AuditEventConstants.SECURITY_EVENT_TYPES).
        assertThat(auditEventService.findAll(AuditLogType.SECURITY, Pageable.unpaged())).anySatisfy(event -> {
            assertThat(event.getType()).isEqualTo(Constants.ADMIN_CHANGE_USER_PASSWORD);
            assertThat(event.getData()).containsEntry("revokedPasskeys", "true").containsEntry("revokedSshKeys", "true").containsEntry("revokedVcsAccessTokens", "true");
        });
    }

    /**
     * The mirror image: an unrelated edit must not cost the user their credentials, otherwise every admin who fixes a
     * typo in a name logs that user out of their repositories.
     */
    @Test
    void anUnrelatedAdminUpdateKeepsTheCredentials() {
        giveUserCredentials();

        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities);
        update.setPassword(null);
        update.setFirstName("Renamed");
        userCreationService.updateUser(userWithAuthorities, update);

        assertThat(reloadUser().getFirstName()).isEqualTo("Renamed");
        assertAllCredentialsKept();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void anAdminPasswordChangeKeepsCredentialsByDefault() throws Exception {
        giveUserCredentials();
        String oldPasswordHash = user.getPassword();

        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities, "new-Password-123");
        update.setName("Test User");
        assertThat(update.isRevokeCredentials()).isFalse();
        request.put("/api/account/admin/users", update, HttpStatus.OK);

        assertPasswordChangedTo(oldPasswordHash, "new-Password-123");
        assertAllCredentialsKept();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void anAdminPasswordChangeRevokesEverythingWhenSelected() throws Exception {
        giveUserCredentials();
        String oldPasswordHash = user.getPassword();

        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities, "new-Password-123");
        update.setName("Test User");
        update.setRevokeCredentials(true);
        assertThat(update.isRevokeCredentials()).isTrue();
        request.put("/api/account/admin/users", update, HttpStatus.OK);

        assertPasswordChangedTo(oldPasswordHash, "new-Password-123");
        assertAllCredentialsRevoked();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void selectingRevocationWithoutChangingThePasswordKeepsCredentials() throws Exception {
        giveUserCredentials();
        String oldPasswordHash = user.getPassword();

        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities);
        update.setName("Renamed User");
        update.setFirstName("Renamed");
        update.setRevokeCredentials(true);
        request.put("/api/account/admin/users", update, HttpStatus.OK);

        User reloaded = reloadUser();
        assertThat(reloaded.getPassword()).isEqualTo(oldPasswordHash);
        assertThat(reloaded.getFirstName()).isEqualTo("Renamed");
        assertThat(reloaded.getActivated()).isTrue();
        assertAllCredentialsKept();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void selectingRevocationForAnExternalUserKeepsCredentials() throws Exception {
        giveUserCredentials();
        user.setInternal(false);
        userRepository.save(user);
        String oldPasswordHash = user.getPassword();

        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities, "new-Password-123");
        update.setName("External User");
        update.setRevokeCredentials(true);
        request.put("/api/account/admin/users", update, HttpStatus.OK);

        User reloaded = reloadUser();
        assertThat(reloaded.isInternal()).isFalse();
        assertThat(reloaded.getPassword()).isEqualTo(oldPasswordHash);
        assertThat(passwordService.checkPasswordMatch("new-Password-123", reloaded.getPassword())).isFalse();
        assertThat(reloaded.getActivated()).isTrue();
        assertAllCredentialsKept();
    }

    @Test
    void revokingIsIdempotentAndSafeOnAnAccountWithoutCredentials() {
        // Runs on every password reset and deactivation, including for accounts that never had any of these credentials.
        accountCredentialRevocationService.revokeAllCredentials(user, "test");
        accountCredentialRevocationService.revokeAllCredentials(user, "test");

        assertAllCredentialsRevoked();
    }
}
