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

public class PasskeyIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "passkeyintegration";

    @Autowired
    PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Autowired
    PasskeyCredentialUtilService passkeyCredentialUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdatePasskeyLabel_Success() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
        PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), "newLabel", existingCredential.getCreatedDate(), existingCredential.getLastUsed());

        request.put("/api/core/passkey/" + modifiedCredential.credentialId(), modifiedCredential, HttpStatus.OK);

        PasskeyCredential modifiedCredentialInDatabase = passkeyCredentialsRepository.findByCredentialId(modifiedCredential.credentialId())
                .orElseThrow(() -> new IllegalStateException("Credential not found"));

        assertThat(modifiedCredentialInDatabase.getCredentialId()).isEqualTo(existingCredential.getCredentialId());
        assertThat(modifiedCredentialInDatabase.getCreatedDate()).isEqualTo(existingCredential.getCreatedDate());
        assertThat(modifiedCredentialInDatabase.getLastUsed()).isEqualTo(existingCredential.getLastUsed());
        assertThat(modifiedCredentialInDatabase.getLabel()).isNotEqualTo(existingCredential.getLabel());
        assertThat(modifiedCredentialInDatabase.getLabel()).isEqualTo(modifiedCredential.label());
    }

}
