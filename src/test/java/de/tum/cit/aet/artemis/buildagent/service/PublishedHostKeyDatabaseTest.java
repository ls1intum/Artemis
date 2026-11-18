package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.service.PublishedHostKeyDatabase.Mode;
import de.tum.cit.aet.artemis.localvc.service.ssh.HashUtils;

class PublishedHostKeyDatabaseTest {

    /** Two fixed EC public keys (X.509, Base64), so the fingerprints are the same in every run. */
    private static final PublicKey PUBLISHED_KEY = publicKey(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEK9YjdhqS1TC106N53CWscGSxaulw0SoJeues5YF4mk5+PU0DjI/W19fhMloYIQkaY2tSdYUpkTJGRkHJ5BZzeg==");

    private static final PublicKey OTHER_KEY = publicKey(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEgZwqLHGs5oMoS34CwJcTENAWjnquYDrLJ9I+4kvrYr77bV59Zv8EOolNtC2DIub52A5hTzgpoHIk0ebtN12lwQ==");

    private static PublicKey publicKey(String base64) {
        try {
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> published(PublicKey... keys) {
        return Arrays.stream(keys).map(key -> HashUtils.getSha256Fingerprint(key)).collect(Collectors.toSet());
    }

    private static boolean accept(PublishedHostKeyDatabase database, PublicKey presented) {
        return database.accept("artemis.example.org", null, presented, null, null);
    }

    @Test
    void acceptsAHostKeyWhoseFingerprintIsPublished() {
        var database = new PublishedHostKeyDatabase(() -> published(PUBLISHED_KEY), Mode.REQUIRED);

        assertThat(accept(database, PUBLISHED_KEY)).isTrue();
    }

    @Test
    void refusesAHostKeyWhoseFingerprintIsNotPublished() {
        var database = new PublishedHostKeyDatabase(() -> published(PUBLISHED_KEY), Mode.IF_AVAILABLE);

        assertThat(accept(database, OTHER_KEY)).isFalse();
    }

    @Test
    void refusesAMismatchEvenWhenTheCoreNodeCannotBeAskedAnymore() {
        var calls = new AtomicInteger();
        Supplier<Set<String>> fetcher = () -> calls.getAndIncrement() == 0 ? published(PUBLISHED_KEY) : Set.of();
        var database = new PublishedHostKeyDatabase(fetcher, Mode.IF_AVAILABLE);
        assertThat(accept(database, PUBLISHED_KEY)).isTrue();

        assertThat(accept(database, OTHER_KEY)).as("the fingerprints that were published before still count").isFalse();
    }

    @Test
    void fetchesTheFingerprintsAgainBeforeRefusingAKeyThatWasReplaced() {
        var calls = new AtomicInteger();
        Supplier<Set<String>> fetcher = () -> calls.getAndIncrement() == 0 ? published(PUBLISHED_KEY) : published(OTHER_KEY);
        var database = new PublishedHostKeyDatabase(fetcher, Mode.REQUIRED);
        assertThat(accept(database, PUBLISHED_KEY)).isTrue();

        assertThat(accept(database, OTHER_KEY)).isTrue();
    }

    @Test
    void continuesWithoutAComparisonWhenNothingIsPublishedAndTheModeAllowsIt() {
        var database = new PublishedHostKeyDatabase(Set::of, Mode.IF_AVAILABLE);

        assertThat(accept(database, PUBLISHED_KEY)).isTrue();
    }

    @Test
    void refusesWhenNothingIsPublishedAndTheModeRequiresAComparison() {
        var database = new PublishedHostKeyDatabase(Set::of, Mode.REQUIRED);

        assertThat(accept(database, PUBLISHED_KEY)).isFalse();
    }

    @Test
    void parsesTheModeFromTheConfiguration() {
        assertThat(Mode.parse(null)).isEqualTo(Mode.IF_AVAILABLE);
        assertThat(Mode.parse("if-available")).isEqualTo(Mode.IF_AVAILABLE);
        assertThat(Mode.parse("REQUIRED")).isEqualTo(Mode.REQUIRED);
        assertThat(Mode.parse(" required ")).isEqualTo(Mode.REQUIRED);
    }

    @Test
    void acceptsEveryKeyOfAnAlgorithmThatTheCoreNodePublishes() {
        // while a host key is replaced, the core node publishes the old and the new key
        var database = new PublishedHostKeyDatabase(() -> published(PUBLISHED_KEY, OTHER_KEY), Mode.REQUIRED);

        assertThat(accept(database, PUBLISHED_KEY)).isTrue();
        assertThat(accept(database, OTHER_KEY)).isTrue();
    }

    @Test
    void doesNotAskAgainWithinThirtySecondsAfterAFailedFetch() {
        var calls = new AtomicInteger();
        var clock = new AtomicLong();
        Supplier<Set<String>> fetcher = () -> {
            calls.incrementAndGet();
            return Set.of();
        };
        var database = new PublishedHostKeyDatabase(fetcher, Mode.IF_AVAILABLE, clock::get);

        // one connection asks once for the cached state and once before it gives up, which is one failed fetch
        assertThat(accept(database, PUBLISHED_KEY)).isTrue();
        assertThat(accept(database, PUBLISHED_KEY)).isTrue();
        assertThat(accept(database, PUBLISHED_KEY)).isTrue();
        assertThat(calls).hasValue(1);

        clock.addAndGet(PublishedHostKeyDatabase.FAILURE_CACHE_DURATION.toNanos() + 1);
        assertThat(accept(database, PUBLISHED_KEY)).isTrue();

        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotAskTheCoreNodeAgainForAKeyThatAnotherConnectionJustFound() {
        var calls = new AtomicInteger();
        Supplier<Set<String>> fetcher = () -> calls.getAndIncrement() == 0 ? published(PUBLISHED_KEY) : published(OTHER_KEY);
        var database = new PublishedHostKeyDatabase(fetcher, Mode.REQUIRED);
        assertThat(accept(database, PUBLISHED_KEY)).isTrue();
        // one connection presents the replaced key, finds it missing in the cache and reads the published fingerprints again
        assertThat(accept(database, OTHER_KEY)).isTrue();
        assertThat(calls).hasValue(2);

        // a second connection with the same key missed the cache before that, and now it is forced to refresh as well
        Set<String> found = database.publishedFingerprints(true, HashUtils.getSha256Fingerprint(OTHER_KEY));

        assertThat(found).isEqualTo(published(OTHER_KEY));
        assertThat(calls).as("the cache already has the key, so the core node is not asked a second time").hasValue(2);
    }

    @Test
    void warnsOnceWhenManyConnectionsReachTheFallbackAtTheSameTime() throws Exception {
        var database = new PublishedHostKeyDatabase(Set::of, Mode.IF_AVAILABLE, () -> 0L);
        int connections = 16;
        var start = new CountDownLatch(1);
        var warnings = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(connections);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int i = 0; i < connections; i++) {
                done.add(executor.submit(() -> {
                    start.await();
                    if (database.shouldWarnAboutMissingFingerprints()) {
                        warnings.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : done) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(warnings).hasValue(1);
    }

    @Test
    void warnsAboutMissingFingerprintsOncePerPeriod() {
        var clock = new AtomicLong();
        var database = new PublishedHostKeyDatabase(Set::of, Mode.IF_AVAILABLE, clock::get);

        assertThat(database.shouldWarnAboutMissingFingerprints()).isTrue();
        assertThat(database.shouldWarnAboutMissingFingerprints()).isFalse();
        assertThat(database.shouldWarnAboutMissingFingerprints()).isFalse();

        clock.addAndGet(PublishedHostKeyDatabase.FAILURE_CACHE_DURATION.toNanos() + 1);

        assertThat(database.shouldWarnAboutMissingFingerprints()).isTrue();
    }

    @Test
    void usesTheFingerprintsOfBeforeWhileTheCoreNodeCannotBeAsked() {
        var calls = new AtomicInteger();
        var clock = new AtomicLong();
        Supplier<Set<String>> fetcher = () -> calls.getAndIncrement() == 0 ? published(PUBLISHED_KEY) : Set.of();
        var database = new PublishedHostKeyDatabase(fetcher, Mode.REQUIRED, clock::get);
        assertThat(accept(database, PUBLISHED_KEY)).isTrue();
        assertThat(calls).hasValue(1);

        // the cached fingerprints are too old now, so the core node is asked again, and it does not answer
        clock.addAndGet(PublishedHostKeyDatabase.CACHE_DURATION.toNanos() + 1);
        assertThat(accept(database, PUBLISHED_KEY)).isTrue();
        assertThat(calls).hasValue(2);
        assertThat(accept(database, OTHER_KEY)).isFalse();
    }
}
