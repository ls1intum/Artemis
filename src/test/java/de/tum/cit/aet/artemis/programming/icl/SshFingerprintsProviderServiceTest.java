package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.BadRequestException;

import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshFingerprintsProviderService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class SshFingerprintsProviderServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Mock
    private SshServer sshServer;

    @Mock
    private KeyPairProvider keyPairProvider;

    private SshFingerprintsProviderService fingerprintsProviderService;

    private String expectedFingerprint;

    @Nested
    class SshFingerprintsProviderServiceShould {

        Map<String, String> expectedFingerprints;

        KeyPair testKeyPair;

        @BeforeEach
        void setup() throws Exception {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            testKeyPair = keyPairGenerator.generateKeyPair();

            expectedFingerprints = new HashMap<>();
            expectedFingerprint = HashUtils.getSha256Fingerprint(testKeyPair.getPublic());
        }

        @Test
        void returnFingerprints() throws GeneralSecurityException, IOException {
            expectedFingerprints.put("RSA", expectedFingerprint);
            doReturn(Collections.singleton(testKeyPair)).when(keyPairProvider).loadKeys(null);
            doReturn(keyPairProvider).when(sshServer).getKeyPairProvider();
            fingerprintsProviderService = new SshFingerprintsProviderService(sshServer);

            var actualFingerprints = fingerprintsProviderService.getSshFingerPrints();

            assertThat(actualFingerprints).isEqualTo(expectedFingerprints);
        }

        @Test
        void notReturnFingerprintsWhenKeysProviderIsNull() throws GeneralSecurityException, IOException {
            doReturn(null).when(sshServer).getKeyPairProvider();
            fingerprintsProviderService = new SshFingerprintsProviderService(sshServer);

            var actualFingerprints = fingerprintsProviderService.getSshFingerPrints();

            assertThat(actualFingerprints).isEqualTo(expectedFingerprints);
        }

        @Test
        void shouldThrowBadRequestExceptionWhenLoadKeysThrowsIOException() throws GeneralSecurityException, IOException {
            doReturn(Collections.singleton(testKeyPair)).when(keyPairProvider).loadKeys(null);
            doThrow(new IOException()).when(keyPairProvider).loadKeys(null);
            doReturn(keyPairProvider).when(sshServer).getKeyPairProvider();
            fingerprintsProviderService = new SshFingerprintsProviderService(sshServer);
            try {
                fingerprintsProviderService.getSshFingerPrints();
            }
            catch (BadRequestException e) {
                return;
            }
            fail("Should have thrown an exception");
        }
    }
}
