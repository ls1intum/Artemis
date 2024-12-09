package de.tum.cit.aet.artemis.programming.service.localvc.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.BadRequestException;

import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Service responsible for providing SSH fingerprints of the SSH server running in Artemis.
 */
@Profile(PROFILE_LOCALVC)
@Service
public class SshFingerprintsProviderService {

    private static final Logger log = LoggerFactory.getLogger(SshFingerprintsProviderService.class);

    private final SshServer sshServer;

    public SshFingerprintsProviderService(SshServer sshServer) {
        this.sshServer = sshServer;
    }

    /**
     * Retrieves the SSH key fingerprints from the stored SSH keys
     *
     * @return a map containing the SSH key fingerprints, where the key is the algorithm
     *         of the public key and the value is its SHA-256 fingerprint.
     * @throws BadRequestException if there is an error loading keys from the SSH server.
     */
    public Map<String, String> getSshFingerPrints() {
        Map<String, String> fingerprints = new HashMap<>();
        KeyPairProvider keyPairProvider = sshServer.getKeyPairProvider();
        if (keyPairProvider != null) {
            try {
                keyPairProvider.loadKeys(null).iterator()
                        .forEachRemaining(keyPair -> fingerprints.put(keyPair.getPublic().getAlgorithm(), HashUtils.getSha256Fingerprint(keyPair.getPublic())));

            }
            catch (IOException | GeneralSecurityException e) {
                log.info("Could not load keys from the ssh server while trying to get SSH key fingerprints", e);
                throw new BadRequestException("Could not load keys from the ssh server");
            }
        }
        return fingerprints;
    }
}
