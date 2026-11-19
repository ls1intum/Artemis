package de.tum.cit.aet.artemis.localvc.service.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import jakarta.ws.rs.BadRequestException;

import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.localvc.dto.SshHostKeyFingerprintDTO;

/**
 * Service responsible for providing SSH fingerprints of the SSH server running in Artemis.
 */
@Profile(PROFILE_LOCALVC)
@Lazy
@Service
public class SshFingerprintsProviderService {

    private static final Logger log = LoggerFactory.getLogger(SshFingerprintsProviderService.class);

    /**
     * How long the fingerprints are kept before the keys are read again. The host key provider of the server decides when keys change: one that reads a directory of key files
     * reads
     * it for every SSH session, so a key file that is added or removed takes effect without a restart. A short time keeps the published fingerprints close to the keys that the
     * server presents, and still means that a request that anyone can send does not read the key files every time.
     */
    static final Duration CACHE_DURATION = Duration.ofSeconds(30);

    private final SshServer sshServer;

    private final LongSupplier nanoClock;

    /** The fingerprints that were read last, with the time they were read. Replaced as a whole. */
    private volatile CachedFingerprints cached;

    @Autowired
    public SshFingerprintsProviderService(SshServer sshServer) {
        this(sshServer, System::nanoTime);
    }

    SshFingerprintsProviderService(SshServer sshServer, LongSupplier nanoClock) {
        this.sshServer = sshServer;
        this.nanoClock = nanoClock;
    }

    /**
     * Retrieves the SSH key fingerprints from the stored SSH keys. If the server holds several keys of one algorithm, the fingerprint of the first one is returned.
     *
     * @return a map containing the SSH key fingerprints, where the key is the algorithm of the public key and the value is its SHA-256 fingerprint.
     * @throws BadRequestException if there is an error loading keys from the SSH server.
     */
    public Map<String, String> getSshFingerPrints() {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        getHostKeyFingerprints().forEach(hostKey -> fingerprints.putIfAbsent(hostKey.algorithm(), hostKey.fingerprint()));
        return fingerprints;
    }

    /**
     * Retrieves the fingerprints of all stored SSH keys, including several keys of one algorithm, as it is the case while a key is replaced.
     * The key files are read again when the fingerprints that were read last are older than {@link #CACHE_DURATION}.
     *
     * @return the SHA-256 fingerprint of every host key, with the algorithm of its public key
     * @throws BadRequestException if there is an error loading keys from the SSH server.
     */
    public List<SshHostKeyFingerprintDTO> getHostKeyFingerprints() {
        CachedFingerprints current = cached;
        if (isFresh(current)) {
            return current.fingerprints();
        }
        synchronized (this) {
            CachedFingerprints currentInLock = cached;
            if (isFresh(currentInLock)) {
                return currentInLock.fingerprints();
            }
            List<SshHostKeyFingerprintDTO> loaded = loadFingerprints();
            // Nothing is kept when no key could be read, so the next request reads again
            if (!loaded.isEmpty()) {
                cached = new CachedFingerprints(loaded, nanoClock.getAsLong());
            }
            return loaded;
        }
    }

    private boolean isFresh(CachedFingerprints fingerprints) {
        return fingerprints != null && nanoClock.getAsLong() - fingerprints.loadedAtNanos() < CACHE_DURATION.toNanos();
    }

    private List<SshHostKeyFingerprintDTO> loadFingerprints() {
        List<SshHostKeyFingerprintDTO> fingerprints = new ArrayList<>();
        KeyPairProvider keyPairProvider = sshServer.getKeyPairProvider();
        if (keyPairProvider != null) {
            try {
                keyPairProvider.loadKeys(null).iterator().forEachRemaining(
                        keyPair -> fingerprints.add(new SshHostKeyFingerprintDTO(keyPair.getPublic().getAlgorithm(), HashUtils.getSha256Fingerprint(keyPair.getPublic()))));
            }
            catch (IOException | GeneralSecurityException e) {
                log.info("Could not load keys from the ssh server while trying to get SSH key fingerprints", e);
                throw new BadRequestException("Could not load keys from the ssh server");
            }
        }
        return List.copyOf(fingerprints);
    }

    private record CachedFingerprints(List<SshHostKeyFingerprintDTO> fingerprints, long loadedAtNanos) {
    }
}
