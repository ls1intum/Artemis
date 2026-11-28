package de.tum.cit.aet.artemis.localvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.localvc.dto.SshHostKeyFingerprintDTO;
import de.tum.cit.aet.artemis.localvc.dto.SshHostKeyFingerprintsDTO;

/**
 * The build agents read the answer of {@code api/localvc/public/ssh-fingerprints}, so its shape is spelled out here.
 */
class SshHostKeyFingerprintsWireFormatTest {

    @Test
    void theAnswerListsEveryHostKeyWithItsAlgorithm() throws Exception {
        var published = new SshHostKeyFingerprintsDTO(
                List.of(new SshHostKeyFingerprintDTO("RSA", "SHA256:a"), new SshHostKeyFingerprintDTO("RSA", "SHA256:b"), new SshHostKeyFingerprintDTO("Ed25519", "SHA256:c")));

        assertThat(JsonObjectMapper.get().writeValueAsString(published)).isEqualTo(
                "{\"hostKeys\":[{\"algorithm\":\"RSA\",\"fingerprint\":\"SHA256:a\"},{\"algorithm\":\"RSA\",\"fingerprint\":\"SHA256:b\"},{\"algorithm\":\"Ed25519\",\"fingerprint\":\"SHA256:c\"}]}");
    }

    @Test
    void theAnswerIsReadBackAsItWasWritten() throws Exception {
        var published = new SshHostKeyFingerprintsDTO(List.of(new SshHostKeyFingerprintDTO("Ed25519", "SHA256:c")));

        var json = JsonObjectMapper.get().writeValueAsString(published);

        assertThat(JsonObjectMapper.get().readValue(json, SshHostKeyFingerprintsDTO.class)).isEqualTo(published);
    }
}
