package de.tum.cit.aet.artemis.programming.service.localvc.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * This class configures the JGit Servlet, which is used to receive Git push and fetch requests for local VC.
 */
@Profile(PROFILE_LOCALVC)
@Service
public class SshFingerprintsProviderService {

    private static final Logger log = LoggerFactory.getLogger(SshFingerprintsProviderService.class);

    private final SshServer sshServer;

    public SshFingerprintsProviderService(SshServer sshServer) {
        this.sshServer = sshServer;
    }

    public Map<String, String> getSshFingerPrints() {
        Map<String, String> fingerprints = new HashMap<>();
        KeyPairProvider keyPairProvider = sshServer.getKeyPairProvider();
        if (keyPairProvider != null) {
            try {
                keyPairProvider.loadKeys(null).iterator()
                        .forEachRemaining(keyPair -> fingerprints.put(keyPair.getPublic().getAlgorithm(), HashUtils.getSha512Fingerprint(keyPair.getPublic())));

            }
            catch (IOException | GeneralSecurityException e) {
                log.info("Could not load keys from the ssh server while trying to get SSH key fingerprints", e);
            }
        }
        return fingerprints;
    }
}
