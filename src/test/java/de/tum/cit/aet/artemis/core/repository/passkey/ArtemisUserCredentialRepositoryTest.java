package de.tum.cit.aet.artemis.core.repository.passkey;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtemisUserCredentialRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "artemisusercredentialrepository";

    @Autowired
    private ArtemisUserCredentialRepository artemisUserCredentialRepository;

    @Autowired
    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    private User superAdminUser;

    private User regularUser;

    private User adminUser;

    enum UserTestCase {

        SUPER_ADMIN("superadmin-passkey", true), REGULAR_USER("regular-user-passkey", false), ADMIN("admin-user-passkey", false);

        private final String label;

        private final boolean expectedAutoApproval;

        UserTestCase(String label, boolean expectedAutoApproval) {
            this.label = label;
            this.expectedAutoApproval = expectedAutoApproval;
        }

        String getLabel() {
            return label;
        }

        boolean isExpectedAutoApproval() {
            return expectedAutoApproval;
        }
    }

    @BeforeAll
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        regularUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // Create super admin user
        superAdminUser = userUtilService.createAndSaveUser(TEST_PREFIX + "superadmin");
        superAdminUser.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY));
        userTestRepository.save(superAdminUser);

        // Create normal admin user
        adminUser = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        adminUser.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        userTestRepository.save(adminUser);
    }

    private User getUserForTestCase(UserTestCase testCase) {
        return switch (testCase) {
            case SUPER_ADMIN -> superAdminUser;
            case REGULAR_USER -> regularUser;
            case ADMIN -> adminUser;
        };
    }

    @ParameterizedTest
    @EnumSource(UserTestCase.class)
    void testSavePasskey(UserTestCase testCase) {
        // Arrange
        User user = getUserForTestCase(testCase);
        CredentialRecord credentialRecord = createCredentialRecord(user, testCase.getLabel());

        // Act
        artemisUserCredentialRepository.save(credentialRecord);

        // Assert
        Optional<PasskeyCredential> savedCredential = passkeyCredentialsRepository.findByCredentialId(credentialRecord.getCredentialId().toBase64UrlString());
        assertThat(savedCredential).isPresent();
        assertThat(savedCredential.get().isSuperAdminApproved()).isEqualTo(testCase.isExpectedAutoApproval());
        assertThat(savedCredential.get().getUser()).isEqualTo(user);
        assertThat(savedCredential.get().getLabel()).isEqualTo(testCase.getLabel());
    }

    @ParameterizedTest
    @EnumSource(UserTestCase.class)
    void testUpdatePasskey(UserTestCase testCase) {
        // Arrange
        User user = getUserForTestCase(testCase);
        CredentialRecord initialCredential = createCredentialRecord(user, "initial-label");
        artemisUserCredentialRepository.save(initialCredential);

        // Create updated credential record with same credential ID but different label
        CredentialRecord updatedCredential = createCredentialRecord(user, "updated-label", initialCredential.getCredentialId());

        // Act
        artemisUserCredentialRepository.save(updatedCredential);

        // Assert
        Optional<PasskeyCredential> savedCredential = passkeyCredentialsRepository.findByCredentialId(updatedCredential.getCredentialId().toBase64UrlString());
        assertThat(savedCredential).isPresent();
        assertThat(savedCredential.get().isSuperAdminApproved()).isEqualTo(testCase.isExpectedAutoApproval());
        assertThat(savedCredential.get().getLabel()).isEqualTo("updated-label");
    }

    /**
     * Creates a CredentialRecord for testing purposes.
     *
     * @param user  the user to associate with the credential
     * @param label the label for the credential
     * @return a CredentialRecord instance
     */
    private CredentialRecord createCredentialRecord(User user, String label) {
        return createCredentialRecord(user, label, generateRandomCredentialId());
    }

    /**
     * Creates a CredentialRecord with a specific credential ID for testing purposes.
     *
     * @param user         the user to associate with the credential
     * @param label        the label for the credential
     * @param credentialId the credential ID to use
     * @return a CredentialRecord instance
     */
    private CredentialRecord createCredentialRecord(User user, String label, Bytes credentialId) {
        byte[] publicKeyBytes = new byte[77];
        new java.security.SecureRandom().nextBytes(publicKeyBytes);

        byte[] attestationObjectBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(attestationObjectBytes);

        return ImmutableCredentialRecord.builder().credentialId(credentialId).userEntityUserId(BytesConverter.longToBytes(user.getId()))
                .publicKey(new ImmutablePublicKeyCose(publicKeyBytes)).signatureCount(0).uvInitialized(true)
                .transports(Set.of(AuthenticatorTransport.INTERNAL, AuthenticatorTransport.HYBRID)).backupEligible(true).backupState(true)
                .attestationObject(new Bytes(attestationObjectBytes)).created(Instant.now()).lastUsed(Instant.now()).label(label).credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .build();
    }

    /**
     * Generates a random credential ID for testing.
     *
     * @return a Bytes object containing a random credential ID
     */
    private Bytes generateRandomCredentialId() {
        byte[] credentialIdBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(credentialIdBytes);
        return new Bytes(credentialIdBytes);
    }
}
