package de.tum.cit.aet.artemis.core.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.AdminPasskeyDTO;
import de.tum.cit.aet.artemis.core.dto.PasskeyDTO;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.util.PasskeyCredentialUtilService;
import de.tum.cit.aet.artemis.lecture.service.SlideUnhideScheduleService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PasskeyIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "passkeyintegration";

    @Autowired
    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Autowired
    private PasskeyCredentialUtilService passkeyCredentialUtilService;

    @MockitoBean
    private SlideUnhideScheduleService slideUnhideScheduleService;

    private void createApprovedPasskeyForSuperAdmin() {
        User superAdmin = userUtilService.getUserByLogin("superadmin");
        PasskeyCredential passkeyCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(superAdmin);
        passkeyCredential.setSuperAdminApproved(true);
        passkeyCredentialsRepository.save(passkeyCredential);
    }

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);

        createApprovedPasskeyForSuperAdmin();
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
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
        assertThat(existingCredential.isSuperAdminApproved()).isFalse();

        PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), existingCredential.getLabel(), existingCredential.getCreatedDate(),
                existingCredential.getLastUsed(), true);

        request.put("/api/core/passkey/" + modifiedCredential.credentialId() + "/approval", modifiedCredential, HttpStatus.OK);

        PasskeyCredential modifiedCredentialInDatabase = passkeyCredentialsRepository.findByCredentialId(modifiedCredential.credentialId())
                .orElseThrow(() -> new IllegalStateException("Credential not found"));

        assertThat(modifiedCredentialInDatabase.getCredentialId()).isEqualTo(existingCredential.getCredentialId());
        assertThat(modifiedCredentialInDatabase.isSuperAdminApproved()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdatePasskeyApproval_AccessDeniedBecauseNotSuperAdmin() throws Exception {
        User user = userUtilService.getUserByLogin("admin");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
        PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), existingCredential.getLabel(), existingCredential.getCreatedDate(),
                existingCredential.getLastUsed(), true);

        request.put("/api/core/passkey/" + modifiedCredential.credentialId() + "/approval", modifiedCredential, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void testUpdatePasskeyApproval_NotFound() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
        PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), existingCredential.getLabel(), existingCredential.getCreatedDate(),
                existingCredential.getLastUsed(), true);

        request.put("/api/core/passkey/idDoesNotExist/approval", modifiedCredential, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void testGetAllPasskeysForAdmin_Success() throws Exception {
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");

        PasskeyCredential credential1 = passkeyCredentialUtilService.createAndSavePasskeyCredential(student1);
        PasskeyCredential credential2 = passkeyCredentialUtilService.createAndSavePasskeyCredential(student2);
        credential2.setSuperAdminApproved(true);
        passkeyCredentialsRepository.save(credential2);

        List<AdminPasskeyDTO> passkeys = request.getList("/api/core/passkey/admin", HttpStatus.OK, AdminPasskeyDTO.class);

        assertThat(passkeys).isNotEmpty();
        assertThat(passkeys).hasSizeGreaterThanOrEqualTo(2);

        AdminPasskeyDTO passkeyDto1 = passkeys.stream().filter(p -> p.credentialId().equals(credential1.getCredentialId())).findFirst().orElseThrow();
        assertThat(passkeyDto1.userLogin()).isEqualTo(student1.getLogin());
        assertThat(passkeyDto1.userName()).isEqualTo(student1.getName());
        assertThat(passkeyDto1.userId()).isEqualTo(student1.getId());
        assertThat(passkeyDto1.label()).isEqualTo(credential1.getLabel());
        assertThat(passkeyDto1.isSuperAdminApproved()).isFalse();

        AdminPasskeyDTO passkeyDto2 = passkeys.stream().filter(p -> p.credentialId().equals(credential2.getCredentialId())).findFirst().orElseThrow();
        assertThat(passkeyDto2.userLogin()).isEqualTo(student2.getLogin());
        assertThat(passkeyDto2.userName()).isEqualTo(student2.getName());
        assertThat(passkeyDto2.userId()).isEqualTo(student2.getId());
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
        passkeyCredentialsRepository.deleteAll();

        List<AdminPasskeyDTO> passkeys = request.getList("/api/core/passkey/admin", HttpStatus.OK, AdminPasskeyDTO.class);

        assertThat(passkeys).isEmpty();
    }

}
