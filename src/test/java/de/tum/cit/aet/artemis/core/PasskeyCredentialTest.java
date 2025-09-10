package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.dto.PasskeyDTO;

class PasskeyCredentialTest {

    @Test
    void testToDto() {
        PasskeyCredential credential = new PasskeyCredential();
        credential.setCredentialId("k4KbTc2mEAI56H0CwVqgn2ckiWI");
        credential.setLabel("Test Label");
        Instant now = Instant.now();
        credential.setLastUsed(now);
        credential.setCreatedDate(now);

        PasskeyDTO dto = credential.toDto();

        assertThat(dto).isNotNull();
        assertThat(dto.credentialId()).isEqualTo("k4KbTc2mEAI56H0CwVqgn2ckiWI");
        assertThat(dto.label()).isEqualTo("Test Label");
        assertThat(dto.lastUsed()).isEqualTo(now);
        assertThat(dto.created()).isEqualTo(now);
    }
}
