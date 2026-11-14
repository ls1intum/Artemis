package de.tum.cit.aet.artemis.buildagent.service;

import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.localvc.service.ssh.HashUtils;

/**
 * Accepts the SSH host key of a core node only if its fingerprint is one that the core node publishes at {@code api/localvc/public/ssh-fingerprints}.
 * <p>
 * The published fingerprints are cached for a short time. When a presented key does not match the cached ones, they are fetched again once, so that a host key
 * that was replaced on the core node is accepted without restarting the agent.
 */
class PublishedHostKeyDatabase implements ServerKeyDatabase {

    /**
     * What an agent does when the fingerprints cannot be obtained from the core node.
     */
    enum Mode {

        /**
         * The clone fails, because the host key cannot be verified.
         */
        REQUIRED,

        /**
         * The clone continues and a warning is logged, which is how agents behaved before the host key was verified.
         */
        IF_AVAILABLE;

        static Mode parse(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return IF_AVAILABLE;
            }
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PublishedHostKeyDatabase.class);

    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);

    private final Supplier<Map<String, String>> fingerprintFetcher;

    private final Mode mode;

    private volatile Map<String, String> cachedFingerprints = Map.of();

    private volatile long cachedAtNanos;

    PublishedHostKeyDatabase(Supplier<Map<String, String>> fingerprintFetcher, Mode mode) {
        this.fingerprintFetcher = fingerprintFetcher;
        this.mode = mode;
    }

    @Override
    public List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress, Configuration config) {
        // No key is known up front: the fingerprints are compared when a key is presented
        return List.of();
    }

    @Override
    public boolean accept(String connectAddress, InetSocketAddress remoteAddress, PublicKey serverKey, Configuration config, @Nullable CredentialsProvider provider) {
        String presented = HashUtils.getSha256Fingerprint(serverKey);

        Map<String, String> published = publishedFingerprints(false);
        if (published.containsValue(presented)) {
            return true;
        }
        // The key may have been replaced since the fingerprints were cached
        published = publishedFingerprints(true);
        if (published.containsValue(presented)) {
            return true;
        }
        if (published.isEmpty()) {
            if (mode == Mode.REQUIRED) {
                log.error("The SSH host key of {} was not accepted: its fingerprint {} could not be compared, because the core node does not publish its fingerprints. "
                        + "Set artemis.version-control.ssh-host-key-verification to if-available to continue without the comparison.", connectAddress, presented);
                return false;
            }
            log.warn("The SSH host key of {} with the fingerprint {} was accepted without a comparison, because the core node does not publish its fingerprints.", connectAddress,
                    presented);
            return true;
        }
        log.error("The SSH host key of {} was not accepted: its fingerprint {} is none of the fingerprints that the core node publishes ({}). "
                + "Check that the build agent connects to the Artemis SSH server and not to another host.", connectAddress, presented, published.values());
        return false;
    }

    private Map<String, String> publishedFingerprints(boolean forceRefresh) {
        long now = System.nanoTime();
        if (!forceRefresh && !cachedFingerprints.isEmpty() && now - cachedAtNanos < CACHE_DURATION.toNanos()) {
            return cachedFingerprints;
        }
        Map<String, String> fetched = fingerprintFetcher.get();
        if (fetched.isEmpty()) {
            // A fingerprint that was published before still counts when the core node cannot be asked right now
            return cachedFingerprints;
        }
        cachedFingerprints = fetched;
        cachedAtNanos = now;
        return fetched;
    }
}
