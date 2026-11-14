package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

    private static Map<String, String> published(KeyPair... keys) {
        return Map.of("EC", HashUtils.getSha256Fingerprint(keys[0].getPublic()));
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
        Supplier<Map<String, String>> fetcher = () -> calls.getAndIncrement() == 0 ? published(publishedKey) : Map.of();
        var database = new PublishedHostKeyDatabase(fetcher, Mode.IF_AVAILABLE);
        assertThat(accept(database, publishedKey)).isTrue();

        assertThat(accept(database, otherKey)).as("the fingerprints that were published before still count").isFalse();
    }

    @Test
    void fetchesTheFingerprintsAgainBeforeRefusingAKeyThatWasReplaced() {
        var calls = new AtomicInteger();
        Supplier<Map<String, String>> fetcher = () -> calls.getAndIncrement() == 0 ? published(publishedKey) : published(otherKey);
        var database = new PublishedHostKeyDatabase(fetcher, Mode.REQUIRED);
        assertThat(accept(database, publishedKey)).isTrue();

        assertThat(accept(database, otherKey)).isTrue();
    }

    @Test
    void continuesWithoutAComparisonWhenNothingIsPublishedAndTheModeAllowsIt() {
        var database = new PublishedHostKeyDatabase(Map::of, Mode.IF_AVAILABLE);

        assertThat(accept(database, publishedKey)).isTrue();
    }

    @Test
    void refusesWhenNothingIsPublishedAndTheModeRequiresAComparison() {
        var database = new PublishedHostKeyDatabase(Map::of, Mode.REQUIRED);

        assertThat(accept(database, publishedKey)).isFalse();
    }

    @Test
    void parsesTheModeFromTheConfiguration() {
        assertThat(Mode.parse(null)).isEqualTo(Mode.IF_AVAILABLE);
        assertThat(Mode.parse("if-available")).isEqualTo(Mode.IF_AVAILABLE);
        assertThat(Mode.parse("REQUIRED")).isEqualTo(Mode.REQUIRED);
        assertThat(Mode.parse(" required ")).isEqualTo(Mode.REQUIRED);
    }
}
