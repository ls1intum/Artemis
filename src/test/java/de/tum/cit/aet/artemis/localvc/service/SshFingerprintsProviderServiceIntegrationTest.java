package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.ws.rs.BadRequestException;

import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localvc.service.ssh.HashUtils;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshFingerprintsProviderService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class SshFingerprintsProviderServiceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private SshServer sshServer;

    private KeyPairProvider keyPairProvider;

    private SshFingerprintsProviderService fingerprintsProviderService;

    private String expectedFingerprint;

    @Nested
    class SshFingerprintsProviderServiceShould {

        Map<String, String> expectedFingerprints;

        KeyPair testKeyPair;

        @BeforeEach
        void setup() throws Exception {
            sshServer = mock(SshServer.class);
            keyPairProvider = mock(KeyPairProvider.class);

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            testKeyPair = keyPairGenerator.generateKeyPair();

            expectedFingerprints = new HashMap<>();
            expectedFingerprint = HashUtils.getSha256Fingerprint(testKeyPair.getPublic());
        }

        @Test
        void returnFingerprints() throws GeneralSecurityException, IOException {
            expectedFingerprints.put("RSA", expectedFingerprint);
            doReturn(Set.of(testKeyPair)).when(keyPairProvider).loadKeys(null);
            doReturn(keyPairProvider).when(sshServer).getKeyPairProvider();
            fingerprintsProviderService = new SshFingerprintsProviderService(sshServer);

            var actualFingerprints = fingerprintsProviderService.getSshFingerPrints();

            assertThat(actualFingerprints).isEqualTo(expectedFingerprints);
        }

        @Test
        void returnEveryKeyOfOneAlgorithm() throws GeneralSecurityException, IOException {
            KeyPair secondKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            doReturn(List.of(testKeyPair, secondKeyPair)).when(keyPairProvider).loadKeys(null);
            doReturn(keyPairProvider).when(sshServer).getKeyPairProvider();
            fingerprintsProviderService = new SshFingerprintsProviderService(sshServer);

            var allFingerprints = fingerprintsProviderService.getAllSshFingerprints();

            assertThat(allFingerprints).containsOnlyKeys("RSA");
            assertThat(allFingerprints.get("RSA")).containsExactly(expectedFingerprint, HashUtils.getSha256Fingerprint(secondKeyPair.getPublic()));
            assertThat(fingerprintsProviderService.getSshFingerPrints()).containsEntry("RSA", expectedFingerprint);
        }

        @Test
        void readTheKeyFilesOnlyOnce() throws GeneralSecurityException, IOException {
            doReturn(Set.of(testKeyPair)).when(keyPairProvider).loadKeys(null);
            doReturn(keyPairProvider).when(sshServer).getKeyPairProvider();
            fingerprintsProviderService = new SshFingerprintsProviderService(sshServer);

            fingerprintsProviderService.getAllSshFingerprints();
            fingerprintsProviderService.getAllSshFingerprints();
            fingerprintsProviderService.getSshFingerPrints();

            verify(keyPairProvider, times(1)).loadKeys(null);
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
            doReturn(Set.of(testKeyPair)).when(keyPairProvider).loadKeys(null);
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
