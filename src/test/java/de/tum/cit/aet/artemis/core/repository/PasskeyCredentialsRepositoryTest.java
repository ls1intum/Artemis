package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.authentication.PasskeyCredentialFactory.createAndSavePasskey;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.PasskeyAdminDTO;
import de.tum.cit.aet.artemis.core.repository.passkey.ArtemisUserCredentialRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for {@link PasskeyCredentialsRepository}.
 */
class PasskeyCredentialsRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "passkeyrepotest";

    @Autowired
    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Autowired
    private ArtemisUserCredentialRepository artemisUserCredentialRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    private User adminUser;

    private User regularUser;

    private User superAdminUser;

    @BeforeEach
    void setUp() {
        adminUser = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        adminUser.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        adminUser = userRepository.save(adminUser);

        regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "student");

        userUtilService.addSuperAdmin(TEST_PREFIX);
        superAdminUser = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");
    }

    @AfterEach
    void tearDown() {
        passkeyCredentialsRepository.deleteAll();
    }

    @Test
    void testFindPasskeysForAdminUsers() {
        createAndSavePasskey(adminUser, "Admin Passkey", true, artemisUserCredentialRepository, passkeyCredentialsRepository);

        // Create passkey for regular user (should not be included)
        createAndSavePasskey(regularUser, "Student Passkey", false, artemisUserCredentialRepository, passkeyCredentialsRepository);

        createAndSavePasskey(superAdminUser, "Super Admin Passkey", true, artemisUserCredentialRepository, passkeyCredentialsRepository);

        // Retrieve all passkeys for admin users
        List<PasskeyAdminDTO> result = passkeyCredentialsRepository.findPasskeysForAdminUsers();

        // Should only include passkeys for admin and super admin users
        assertThat(result).hasSize(2);
        assertThat(result).extracting(PasskeyAdminDTO::userId).containsExactlyInAnyOrder(adminUser.getId(), superAdminUser.getId());
        assertThat(result).extracting(PasskeyAdminDTO::label).containsExactlyInAnyOrder("Admin Passkey", "Super Admin Passkey");
        assertThat(result).extracting(PasskeyAdminDTO::userLogin).containsExactlyInAnyOrder(adminUser.getLogin(), superAdminUser.getLogin());
    }

    @Test
    void testFindPasskeysForAdminUsers_NoAdminPasskeys() {
        createAndSavePasskey(regularUser, "Student Passkey", false, artemisUserCredentialRepository, passkeyCredentialsRepository);

        List<PasskeyAdminDTO> result = passkeyCredentialsRepository.findPasskeysForAdminUsers();

        assertThat(result).isEmpty();
    }

    @Test
    void testFindPasskeysForAdminUsers_VerifyDTOFields() {
        // Create passkey for admin user with specific data
        PasskeyCredential adminPasskey = createAndSavePasskey(adminUser, "Test Passkey Label", true, artemisUserCredentialRepository, passkeyCredentialsRepository);
        adminPasskey.setLastUsed(Instant.now().minusSeconds(86400)); // 1 day ago
        passkeyCredentialsRepository.save(adminPasskey);

        // Retrieve passkeys
        List<PasskeyAdminDTO> result = passkeyCredentialsRepository.findPasskeysForAdminUsers();

        assertThat(result).hasSize(1);
        PasskeyAdminDTO dto = result.getFirst();

        // Verify all DTO fields are properly populated
        assertThat(dto.credentialId()).isEqualTo(adminPasskey.getCredentialId());
        assertThat(dto.label()).isEqualTo("Test Passkey Label");
        assertThat(dto.created()).isNotNull();
        assertThat(dto.lastUsed()).isNotNull();
        assertThat(dto.isSuperAdminApproved()).isTrue();
        assertThat(dto.userId()).isEqualTo(adminUser.getId());
        assertThat(dto.userLogin()).isEqualTo(adminUser.getLogin());
        assertThat(dto.userName()).contains(adminUser.getFirstName()).contains(adminUser.getLastName());
    }
}
