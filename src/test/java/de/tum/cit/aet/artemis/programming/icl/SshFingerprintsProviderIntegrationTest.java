package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshFingerprintsProviderService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class SshFingerprintsProviderIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "sshFingerprintsTest";

    @MockitoSpyBean
    private SshFingerprintsProviderService fingerprintsProviderService;

    private String expectedFingerprint;

    @BeforeEach
    void setup() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair testKeyPair = keyPairGenerator.generateKeyPair();

        expectedFingerprint = HashUtils.getSha256Fingerprint(testKeyPair.getPublic());
    }

    @Nested
    class SshFingerprintsProviderShould {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void returnFingerprints() throws Exception {
            Map<String, String> expectedFingerprints = new HashMap<>();
            expectedFingerprints.put("RSA", expectedFingerprint);
            doReturn(expectedFingerprints).when(fingerprintsProviderService).getSshFingerPrints();

            var response = request.get("/api/ssh-fingerprints", HttpStatus.OK, Map.class);

            assertThat(response.get("RSA")).isNotNull();
            assertThat(response.get("RSA")).isEqualTo(expectedFingerprint);
        }
    }
}
