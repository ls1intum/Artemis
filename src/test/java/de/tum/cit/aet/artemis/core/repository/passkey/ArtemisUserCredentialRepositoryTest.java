package de.tum.cit.aet.artemis.core.repository.passkey;

import static de.tum.cit.aet.artemis.core.authentication.CredentialRecordFactory.createCredentialRecord;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.webauthn.api.CredentialRecord;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
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
}
