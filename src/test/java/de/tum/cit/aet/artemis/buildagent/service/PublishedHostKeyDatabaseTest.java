package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.buildagent.service.PublishedHostKeyDatabase.Mode;
import de.tum.cit.aet.artemis.localvc.service.ssh.HashUtils;

class PublishedHostKeyDatabaseTest {

    private static KeyPair publishedKey;

    private static KeyPair otherKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        publishedKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair();
    }

    private static Set<String> published(KeyPair... keys) {
        return Arrays.stream(keys).map(key -> HashUtils.getSha256Fingerprint(key.getPublic())).collect(Collectors.toSet());
    }

    private static boolean accept(PublishedHostKeyDatabase database, KeyPair presented) {
        return database.accept("artemis.example.org", null, presented.getPublic(), null, null);
    }

    @Test
    void acceptsAHostKeyWhoseFingerprintIsPublished() {
        var database = new PublishedHostKeyDatabase(() -> published(publishedKey), Mode.REQUIRED);

        assertThat(accept(database, publishedKey)).isTrue();
    }

    @Test
    void refusesAHostKeyWhoseFingerprintIsNotPublished() {
        var database = new PublishedHostKeyDatabase(() -> published(publishedKey), Mode.IF_AVAILABLE);

        assertThat(accept(database, otherKey)).isFalse();
    }

    @Test
    void refusesAMismatchEvenWhenTheCoreNodeCannotBeAskedAnymore() {
        var calls = new AtomicInteger();
        Supplier<Set<String>> fetcher = () -> calls.getAndIncrement() == 0 ? published(publishedKey) : Set.of();
        var database = new PublishedHostKeyDatabase(fetcher, Mode.IF_AVAILABLE);
        assertThat(accept(database, publishedKey)).isTrue();

        assertThat(accept(database, otherKey)).as("the fingerprints that were published before still count").isFalse();
    }

    @Test
    void fetchesTheFingerprintsAgainBeforeRefusingAKeyThatWasReplaced() {
        var calls = new AtomicInteger();
        Supplier<Set<String>> fetcher = () -> calls.getAndIncrement() == 0 ? published(publishedKey) : published(otherKey);
        var database = new PublishedHostKeyDatabase(fetcher, Mode.REQUIRED);
        assertThat(accept(database, publishedKey)).isTrue();

        assertThat(accept(database, otherKey)).isTrue();
    }

    @Test
    void continuesWithoutAComparisonWhenNothingIsPublishedAndTheModeAllowsIt() {
        var database = new PublishedHostKeyDatabase(Set::of, Mode.IF_AVAILABLE);

        assertThat(accept(database, publishedKey)).isTrue();
    }

    @Test
    void refusesWhenNothingIsPublishedAndTheModeRequiresAComparison() {
        var database = new PublishedHostKeyDatabase(Set::of, Mode.REQUIRED);

        assertThat(accept(database, publishedKey)).isFalse();
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
        var database = new PublishedHostKeyDatabase(() -> published(publishedKey, otherKey), Mode.REQUIRED);

        assertThat(accept(database, publishedKey)).isTrue();
        assertThat(accept(database, otherKey)).isTrue();
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
        assertThat(accept(database, publishedKey)).isTrue();
        assertThat(accept(database, publishedKey)).isTrue();
        assertThat(accept(database, publishedKey)).isTrue();
        assertThat(calls).hasValue(1);

        clock.addAndGet(PublishedHostKeyDatabase.FAILURE_CACHE_DURATION.toNanos() + 1);
        assertThat(accept(database, publishedKey)).isTrue();

        assertThat(calls).hasValue(2);
    }

    @Test
    void logsTheMissingFingerprintsOncePerPeriod() {
        var clock = new AtomicLong();
        var database = new PublishedHostKeyDatabase(Set::of, Mode.IF_AVAILABLE, clock::get);
        Logger logger = (Logger) LoggerFactory.getLogger(PublishedHostKeyDatabase.class);
        // another test of the same JVM may have changed the level of this logger
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            accept(database, publishedKey);
            accept(database, publishedKey);
            accept(database, publishedKey);
            assertThat(appender.list).hasSize(1);

            clock.addAndGet(PublishedHostKeyDatabase.FAILURE_CACHE_DURATION.toNanos() + 1);
            accept(database, publishedKey);

            assertThat(appender.list).hasSize(2);
        }
        finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void usesTheFingerprintsOfBeforeWhileTheCoreNodeCannotBeAsked() {
        var calls = new AtomicInteger();
        var clock = new AtomicLong();
        Supplier<Set<String>> fetcher = () -> calls.getAndIncrement() == 0 ? published(publishedKey) : Set.of();
        var database = new PublishedHostKeyDatabase(fetcher, Mode.REQUIRED, clock::get);
        assertThat(accept(database, publishedKey)).isTrue();

        clock.addAndGet(PublishedHostKeyDatabase.FAILURE_CACHE_DURATION.toNanos());
        assertThat(accept(database, publishedKey)).isTrue();
        assertThat(accept(database, otherKey)).isFalse();
    }
}
