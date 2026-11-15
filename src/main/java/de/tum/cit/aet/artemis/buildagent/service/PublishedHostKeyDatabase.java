package de.tum.cit.aet.artemis.buildagent.service;

import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongSupplier;
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
 * that was replaced on the core node is accepted without restarting the agent. A fetch that fails is remembered for 30 seconds, so that a core node that cannot be
 * reached costs one request with its timeout per period and not two for every connection, and the warning about it is logged once per period as well.
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

    /** How long a failed fetch is remembered, during which the core node is not asked again and the warning is not repeated. */
    static final Duration FAILURE_CACHE_DURATION = Duration.ofSeconds(30);

    private final Supplier<Set<String>> fingerprintFetcher;

    private final Mode mode;

    private final LongSupplier nanoClock;

    private volatile Set<String> cachedFingerprints = Set.of();

    private volatile long cachedAtNanos;

    private volatile boolean lastFetchFailed;

    private volatile long lastFailureAtNanos;

    private volatile boolean warnedAboutMissingFingerprints;

    private volatile long lastWarningAtNanos;

    PublishedHostKeyDatabase(Supplier<Set<String>> fingerprintFetcher, Mode mode) {
        this(fingerprintFetcher, mode, System::nanoTime);
    }

    PublishedHostKeyDatabase(Supplier<Set<String>> fingerprintFetcher, Mode mode, LongSupplier nanoClock) {
        this.fingerprintFetcher = fingerprintFetcher;
        this.mode = mode;
        this.nanoClock = nanoClock;
    }

    @Override
    public List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress, Configuration config) {
        // No key is known up front: the fingerprints are compared when a key is presented
        return List.of();
    }

    @Override
    public boolean accept(String connectAddress, InetSocketAddress remoteAddress, PublicKey serverKey, Configuration config, @Nullable CredentialsProvider provider) {
        String presented = HashUtils.getSha256Fingerprint(serverKey);

        Set<String> published = publishedFingerprints(false);
        if (published.contains(presented)) {
            return true;
        }
        // The key may have been replaced since the fingerprints were cached
        published = publishedFingerprints(true);
        if (published.contains(presented)) {
            return true;
        }
        if (published.isEmpty()) {
            if (mode == Mode.REQUIRED) {
                log.error("The SSH host key of {} was not accepted: its fingerprint {} could not be compared, because the core node does not publish its fingerprints. "
                        + "Set artemis.version-control.ssh-host-key-verification to if-available to continue without the comparison.", connectAddress, presented);
                return false;
            }
            if (shouldWarnAboutMissingFingerprints()) {
                log.warn("The SSH host key of {} with the fingerprint {} was accepted without a comparison, because the core node does not publish its fingerprints.",
                        connectAddress, presented);
            }
            return true;
        }
        log.error("The SSH host key of {} was not accepted: its fingerprint {} is none of the fingerprints that the core node publishes ({}). "
                + "Check that the build agent connects to the Artemis SSH server and not to another host.", connectAddress, presented, published);
        return false;
    }

    private boolean shouldWarnAboutMissingFingerprints() {
        long now = nanoClock.getAsLong();
        if (warnedAboutMissingFingerprints && now - lastWarningAtNanos < FAILURE_CACHE_DURATION.toNanos()) {
            return false;
        }
        warnedAboutMissingFingerprints = true;
        lastWarningAtNanos = now;
        return true;
    }

    private synchronized Set<String> publishedFingerprints(boolean forceRefresh) {
        long now = nanoClock.getAsLong();
        if (!forceRefresh && !cachedFingerprints.isEmpty() && now - cachedAtNanos < CACHE_DURATION.toNanos()) {
            return cachedFingerprints;
        }
        if (lastFetchFailed && now - lastFailureAtNanos < FAILURE_CACHE_DURATION.toNanos()) {
            // The core node did not answer a moment ago, so it is not asked again for every connection
            return cachedFingerprints;
        }
        Set<String> fetched = fingerprintFetcher.get();
        if (fetched.isEmpty()) {
            // A fingerprint that was published before still counts when the core node cannot be asked right now
            lastFetchFailed = true;
            lastFailureAtNanos = now;
            return cachedFingerprints;
        }
        lastFetchFailed = false;
        cachedFingerprints = fetched;
        cachedAtNanos = now;
        return fetched;
    }
}
