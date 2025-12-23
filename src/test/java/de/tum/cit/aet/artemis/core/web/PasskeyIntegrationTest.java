package de.tum.cit.aet.artemis.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.AdminPasskeyDTO;
import de.tum.cit.aet.artemis.core.dto.PasskeyDTO;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.service.PasskeyAuthenticationService;
import de.tum.cit.aet.artemis.core.util.PasskeyCredentialUtilService;
import de.tum.cit.aet.artemis.lecture.service.SlideUnhideScheduleService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PasskeyIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "passkeyintegration";

    @Autowired
    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Autowired
    private PasskeyCredentialUtilService passkeyCredentialUtilService;

    @Autowired
    private PasskeyResource passkeyResource;

    @MockitoBean
    private SlideUnhideScheduleService slideUnhideScheduleService;

    @MockitoBean
    private PasskeyAuthenticationService passkeyAuthenticationService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdatePasskeyLabel_Success() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
        PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), "newLabel", existingCredential.getCreatedDate(), existingCredential.getLastUsed(),
                false);

        request.put("/api/core/passkey/" + modifiedCredential.credentialId(), modifiedCredential, HttpStatus.OK);

        PasskeyCredential modifiedCredentialInDatabase = passkeyCredentialsRepository.findByCredentialId(modifiedCredential.credentialId())
                .orElseThrow(() -> new IllegalStateException("Credential not found"));

        assertThat(modifiedCredentialInDatabase.getCredentialId()).isEqualTo(existingCredential.getCredentialId());
        assertThat(modifiedCredentialInDatabase.getLabel()).isNotEqualTo(existingCredential.getLabel());
        assertThat(modifiedCredentialInDatabase.getLabel()).isEqualTo(modifiedCredential.label());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "ANONYMOUS")
    void testUpdatePasskeyLabel_AccessDeniedBecauseOfRole() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
        PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), "newLabel", existingCredential.getCreatedDate(), existingCredential.getLastUsed(),
                false);

        request.put("/api/core/passkey/" + modifiedCredential.credentialId(), modifiedCredential, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdatePasskeyLabel_NotFoundBecausePasskeyBelongsToSomebodyElse() throws Exception {
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(student2);
        PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), "newLabel", existingCredential.getCreatedDate(), existingCredential.getLastUsed(),
                false);

        request.put("/api/core/passkey/" + modifiedCredential.credentialId(), modifiedCredential, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdatePasskeyLabel_NotFound() throws Exception {
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(student2);
        PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), "newLabel", existingCredential.getCreatedDate(), existingCredential.getLastUsed(),
                false);

        request.put("/api/core/passkey/" + modifiedCredential.credentialId() + "idDoesNotExist", modifiedCredential, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void testUpdatePasskeyApproval_Success() throws Exception {
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
        assertThat(existingCredential.isSuperAdminApproved()).isFalse();

        request.put("/api/core/passkey/" + existingCredential.getCredentialId() + "/approval", true, HttpStatus.OK);

        PasskeyCredential modifiedCredentialInDatabase = passkeyCredentialsRepository.findByCredentialId(existingCredential.getCredentialId())
                .orElseThrow(() -> new IllegalStateException("Credential not found"));

        assertThat(modifiedCredentialInDatabase.getCredentialId()).isEqualTo(existingCredential.getCredentialId());
        assertThat(modifiedCredentialInDatabase.isSuperAdminApproved()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdatePasskeyApproval_AccessDeniedBecauseNotSuperAdmin() throws Exception {
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);
        User user = userUtilService.getUserByLogin("admin");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);

        request.put("/api/core/passkey/" + existingCredential.getCredentialId() + "/approval", true, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void testUpdatePasskeyApproval_NotFound() throws Exception {
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        request.put("/api/core/passkey/idDoesNotExist/approval", true, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void testGetAllPasskeysForAdmin_Success() throws Exception {
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        // Create admin users with ADMIN authority
        User admin1 = userUtilService.createAndSaveUser(TEST_PREFIX + "admin1");
        admin1.setAuthorities(Set.of(de.tum.cit.aet.artemis.core.domain.Authority.ADMIN_AUTHORITY));
        userTestRepository.save(admin1);

        User admin2 = userUtilService.createAndSaveUser(TEST_PREFIX + "admin2");
        admin2.setAuthorities(Set.of(de.tum.cit.aet.artemis.core.domain.Authority.ADMIN_AUTHORITY));
        userTestRepository.save(admin2);

        PasskeyCredential credential1 = passkeyCredentialUtilService.createAndSavePasskeyCredential(admin1);
        PasskeyCredential credential2 = passkeyCredentialUtilService.createAndSavePasskeyCredential(admin2);
        credential2.setSuperAdminApproved(true);
        passkeyCredentialsRepository.save(credential2);

        List<AdminPasskeyDTO> passkeys = request.getList("/api/core/passkey/admin", HttpStatus.OK, AdminPasskeyDTO.class);

        assertThat(passkeys).isNotEmpty();
        assertThat(passkeys).hasSizeGreaterThanOrEqualTo(2);

        AdminPasskeyDTO passkeyDto1 = passkeys.stream().filter(p -> p.credentialId().equals(credential1.getCredentialId())).findFirst().orElseThrow();
        assertThat(passkeyDto1.userLogin()).isEqualTo(admin1.getLogin());
        assertThat(passkeyDto1.userName()).isEqualTo(admin1.getName());
        assertThat(passkeyDto1.userId()).isEqualTo(admin1.getId());
        assertThat(passkeyDto1.label()).isEqualTo(credential1.getLabel());
        assertThat(passkeyDto1.isSuperAdminApproved()).isFalse();

        AdminPasskeyDTO passkeyDto2 = passkeys.stream().filter(p -> p.credentialId().equals(credential2.getCredentialId())).findFirst().orElseThrow();
        assertThat(passkeyDto2.userLogin()).isEqualTo(admin2.getLogin());
        assertThat(passkeyDto2.userName()).isEqualTo(admin2.getName());
        assertThat(passkeyDto2.userId()).isEqualTo(admin2.getId());
        assertThat(passkeyDto2.label()).isEqualTo(credential2.getLabel());
        assertThat(passkeyDto2.isSuperAdminApproved()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllPasskeysForAdmin_AccessDeniedBecauseNotSuperAdmin() throws Exception {
        request.get("/api/core/passkey/admin", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllPasskeysForAdmin_AccessDeniedBecauseStudent() throws Exception {
        request.get("/api/core/passkey/admin", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void testGetAllPasskeysForAdmin_EmptyListWhenNoPasskeys() throws Exception {
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        passkeyCredentialsRepository.deleteAll();

        List<AdminPasskeyDTO> passkeys = request.getList("/api/core/passkey/admin", HttpStatus.OK, AdminPasskeyDTO.class);

        assertThat(passkeys).isEmpty();
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void testGetAllPasskeysForAdmin_OnlyReturnsAdminPasskeys() throws Exception {
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        // Create admin users with ADMIN authority
        User admin1 = userUtilService.createAndSaveUser(TEST_PREFIX + "adminuser1");
        admin1.setAuthorities(Set.of(de.tum.cit.aet.artemis.core.domain.Authority.ADMIN_AUTHORITY));
        userTestRepository.save(admin1);

        // Create regular users (students, instructors, etc.) without ADMIN authority
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User instructor = userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1");
        instructor.setAuthorities(Set.of(de.tum.cit.aet.artemis.core.domain.Authority.INSTRUCTOR_AUTHORITY));
        userTestRepository.save(instructor);

        // Create passkeys for all users
        PasskeyCredential adminCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(admin1);
        PasskeyCredential studentCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(student);
        PasskeyCredential instructorCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(instructor);

        // Fetch all admin passkeys
        List<AdminPasskeyDTO> passkeys = request.getList("/api/core/passkey/admin", HttpStatus.OK, AdminPasskeyDTO.class);

        // Verify only admin passkey is returned
        assertThat(passkeys).isNotEmpty();
        assertThat(passkeys).anyMatch(passkey -> passkey.credentialId().equals(adminCredential.getCredentialId()));
        assertThat(passkeys).noneMatch(passkey -> passkey.credentialId().equals(studentCredential.getCredentialId()));
        assertThat(passkeys).noneMatch(passkey -> passkey.credentialId().equals(instructorCredential.getCredentialId()));

        // Verify admin passkey details
        AdminPasskeyDTO adminPasskeyDto = passkeys.stream().filter(passkey -> passkey.credentialId().equals(adminCredential.getCredentialId())).findFirst().orElseThrow();
        assertThat(adminPasskeyDto.userLogin()).isEqualTo(admin1.getLogin());
        assertThat(adminPasskeyDto.userId()).isEqualTo(admin1.getId());
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void testUpdatePasskeyApproval_RevokeApprovalForRegularUser() throws Exception {
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);

        // Approve the passkey first
        existingCredential.setSuperAdminApproved(true);
        passkeyCredentialsRepository.save(existingCredential);
        assertThat(existingCredential.isSuperAdminApproved()).isTrue();

        // Revoke approval
        request.put("/api/core/passkey/" + existingCredential.getCredentialId() + "/approval", false, HttpStatus.OK);

        // Verify the approval was revoked
        PasskeyCredential credentialInDatabase = passkeyCredentialsRepository.findByCredentialId(existingCredential.getCredentialId())
                .orElseThrow(() -> new IllegalStateException("Credential not found"));
        assertThat(credentialInDatabase.isSuperAdminApproved()).isFalse();
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void testUpdatePasskeyApproval_CannotRevokeInternalAdminPasskeyApproval() throws Exception {
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        // Set up internal admin username
        String internalAdminLogin = "artemis_admin";
        ReflectionTestUtils.setField(passkeyResource, "artemisInternalAdminUsername", Optional.of(internalAdminLogin));

        // Create internal admin user and passkey
        User internalAdminUser = userUtilService.createAndSaveUser(internalAdminLogin);
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(internalAdminUser);

        // Approve the passkey first
        existingCredential.setSuperAdminApproved(true);
        passkeyCredentialsRepository.save(existingCredential);

        // Try to revoke approval
        request.put("/api/core/passkey/" + existingCredential.getCredentialId() + "/approval", false, HttpStatus.BAD_REQUEST);

        // Verify the approval was not revoked
        PasskeyCredential credentialInDatabase = passkeyCredentialsRepository.findByCredentialId(existingCredential.getCredentialId())
                .orElseThrow(() -> new IllegalStateException("Credential not found"));
        assertThat(credentialInDatabase.isSuperAdminApproved()).isTrue();
    }

}
