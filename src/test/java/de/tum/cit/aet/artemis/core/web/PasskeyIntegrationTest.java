package de.tum.cit.aet.artemis.core.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.PasskeyDTO;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.util.PasskeyCredentialUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PasskeyIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "passkeyintegration";

    @Autowired
    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Autowired
    private PasskeyCredentialUtilService passkeyCredentialUtilService;

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

}
