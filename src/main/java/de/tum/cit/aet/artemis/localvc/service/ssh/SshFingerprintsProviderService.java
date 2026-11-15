package de.tum.cit.aet.artemis.localvc.service.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.BadRequestException;

import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Service responsible for providing SSH fingerprints of the SSH server running in Artemis.
 */
@Profile(PROFILE_LOCALVC)
@Lazy
@Service
public class SshFingerprintsProviderService {

    private static final Logger log = LoggerFactory.getLogger(SshFingerprintsProviderService.class);

    private final SshServer sshServer;

    /**
     * The fingerprints by algorithm, read from the key files once. The host keys of the SSH server do not change while it runs, so the cache is never invalidated:
     * after the keys were replaced on disk, the server has to be restarted, which also makes it present the new keys.
     */
    private volatile Map<String, List<String>> cachedFingerprints;

    public SshFingerprintsProviderService(SshServer sshServer) {
        this.sshServer = sshServer;
    }

    /**
     * Retrieves the SSH key fingerprints from the stored SSH keys. If the server holds several keys of one algorithm, the fingerprint of the first one is returned.
     *
     * @return a map containing the SSH key fingerprints, where the key is the algorithm of the public key and the value is its SHA-256 fingerprint.
     * @throws BadRequestException if there is an error loading keys from the SSH server.
     */
    public Map<String, String> getSshFingerPrints() {
        Map<String, String> fingerprints = new HashMap<>();
        getAllSshFingerprints().forEach((algorithm, values) -> fingerprints.put(algorithm, values.getFirst()));
        return fingerprints;
    }

    /**
     * Retrieves the fingerprints of all stored SSH keys, including several keys of one algorithm, as it is the case while a key is replaced.
     * The key files are read on the first call only.
     *
     * @return the SHA-256 fingerprints by the algorithm of the public key
     * @throws BadRequestException if there is an error loading keys from the SSH server.
     */
    public Map<String, List<String>> getAllSshFingerprints() {
        Map<String, List<String>> cached = cachedFingerprints;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedFingerprints != null) {
                return cachedFingerprints;
            }
            Map<String, List<String>> loaded = loadFingerprints();
            if (!loaded.isEmpty()) {
                cachedFingerprints = loaded;
            }
            return loaded;
        }
    }

    private Map<String, List<String>> loadFingerprints() {
        Map<String, List<String>> fingerprints = new LinkedHashMap<>();
        KeyPairProvider keyPairProvider = sshServer.getKeyPairProvider();
        if (keyPairProvider != null) {
            try {
                keyPairProvider.loadKeys(null).iterator().forEachRemaining(keyPair -> fingerprints
                        .computeIfAbsent(keyPair.getPublic().getAlgorithm(), algorithm -> new ArrayList<>()).add(HashUtils.getSha256Fingerprint(keyPair.getPublic())));
            }
            catch (IOException | GeneralSecurityException e) {
                log.info("Could not load keys from the ssh server while trying to get SSH key fingerprints", e);
                throw new BadRequestException("Could not load keys from the ssh server");
            }
        }
        return fingerprints;
    }
}
