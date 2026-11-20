package de.tum.cit.aet.artemis.localvc.service.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.concurrent.atomic.AtomicLong;

import jakarta.ws.rs.BadRequestException;

import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localvc.dto.SshHostKeyFingerprintDTO;

class SshFingerprintsProviderServiceTest {

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

            var allFingerprints = fingerprintsProviderService.getHostKeyFingerprints();

            assertThat(allFingerprints).containsExactly(new SshHostKeyFingerprintDTO("RSA", expectedFingerprint),
                    new SshHostKeyFingerprintDTO("RSA", HashUtils.getSha256Fingerprint(secondKeyPair.getPublic())));
            assertThat(fingerprintsProviderService.getSshFingerPrints()).containsExactly(Map.entry("RSA", expectedFingerprint));
        }

        @Test
        void keepTheFingerprintsForTheCacheDurationOnly() throws GeneralSecurityException, IOException {
            KeyPair secondKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            doReturn(Set.of(testKeyPair)).doReturn(List.of(testKeyPair, secondKeyPair)).when(keyPairProvider).loadKeys(null);
            doReturn(keyPairProvider).when(sshServer).getKeyPairProvider();
            AtomicLong nanos = new AtomicLong(1_000);
            fingerprintsProviderService = new SshFingerprintsProviderService(sshServer, nanos::get);

            assertThat(fingerprintsProviderService.getHostKeyFingerprints()).hasSize(1);
            // nothing is read again just before the time is over, also not for the other method
            nanos.addAndGet(SshFingerprintsProviderService.CACHE_DURATION.toNanos() - 1);
            assertThat(fingerprintsProviderService.getHostKeyFingerprints()).hasSize(1);
            assertThat(fingerprintsProviderService.getSshFingerPrints()).hasSize(1);
            verify(keyPairProvider, times(1)).loadKeys(null);

            // when it is over, a key that was added in the meantime is published
            nanos.addAndGet(1);
            assertThat(fingerprintsProviderService.getHostKeyFingerprints()).hasSize(2);
            verify(keyPairProvider, times(2)).loadKeys(null);
        }

        @Test
        void publishNoKeyThatWasRemovedOnceTheCacheDurationIsOver() throws GeneralSecurityException, IOException {
            KeyPair secondKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            doReturn(List.of(testKeyPair, secondKeyPair)).doReturn(Set.of(secondKeyPair)).when(keyPairProvider).loadKeys(null);
            doReturn(keyPairProvider).when(sshServer).getKeyPairProvider();
            AtomicLong nanos = new AtomicLong(1_000);
            fingerprintsProviderService = new SshFingerprintsProviderService(sshServer, nanos::get);
            assertThat(fingerprintsProviderService.getHostKeyFingerprints()).hasSize(2);

            nanos.addAndGet(SshFingerprintsProviderService.CACHE_DURATION.toNanos());

            assertThat(fingerprintsProviderService.getHostKeyFingerprints()).extracting(SshHostKeyFingerprintDTO::fingerprint)
                    .containsExactly(HashUtils.getSha256Fingerprint(secondKeyPair.getPublic()));
        }

        @Test
        void readTheKeyFilesAgainAfterAFailureAndAfterNoKeyWasFound() throws GeneralSecurityException, IOException {
            doThrow(new IOException()).doReturn(Set.of()).doReturn(Set.of(testKeyPair)).when(keyPairProvider).loadKeys(null);
            doReturn(keyPairProvider).when(sshServer).getKeyPairProvider();
            fingerprintsProviderService = new SshFingerprintsProviderService(sshServer, () -> 1_000L);

            assertThatThrownBy(() -> fingerprintsProviderService.getHostKeyFingerprints()).isInstanceOf(BadRequestException.class);
            assertThat(fingerprintsProviderService.getHostKeyFingerprints()).isEmpty();
            assertThat(fingerprintsProviderService.getHostKeyFingerprints()).hasSize(1);
            verify(keyPairProvider, times(3)).loadKeys(null);
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
