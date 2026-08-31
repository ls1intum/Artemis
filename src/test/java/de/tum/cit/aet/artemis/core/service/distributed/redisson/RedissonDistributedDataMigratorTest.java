package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.RELEASE_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.namespaceFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.DockerClientFactory;

import com.redis.testcontainers.RedisStackContainer;

/**
 * Covers the namespace migration against a real Redis, which is the only way to exercise the drain: the semantics that
 * matter here (an atomic take, a pattern delete, a version key written last) are Redis behaviour rather than ours.
 */
// requires docker for testContainers to start a test redis instance
@EnabledIf("isDockerAvailable")
class RedissonDistributedDataMigratorTest {

    private static final int PREVIOUS_VERSION = VERSION - 1;

    private static RedisStackContainer redis;

    private static RedissonClient redissonClient;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void beforeAll() {
        redis = new RedisStackContainer(RedisStackContainer.DEFAULT_IMAGE_NAME.withTag("7.4.0-v8"));
        redis.start();
        Config config = new Config();
        // The same codec the provider installs, so values written here round-trip exactly as production ones do.
        config.setCodec(new BackwardCompatibleSerializationCodec());
        config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        redissonClient = Redisson.create(config);
    }

    @AfterAll
    static void afterAll() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeEach
    void clearStore() {
        redissonClient.getKeys().flushall();
    }

    private static RedissonDistributedDataMigrator migrationService() {
        return new RedissonDistributedDataMigrator(redissonClient, "1.2.3");
    }

    private static String storedVersion() {
        return redissonClient.<String>getBucket(VERSION_KEY, StringCodec.INSTANCE).get();
    }

    @Test
    void testClaimsAStoreThatHasNoVersionYet() {
        migrationService().migrateToCurrentVersion();

        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
        assertThat(redissonClient.<String>getBucket(RELEASE_KEY, StringCodec.INSTANCE).get()).isEqualTo("1.2.3");
    }

    @Test
    void testDoesNothingWhenTheStoreIsAlreadyCurrent() {
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(VERSION));
        redissonClient.getMap(namespaceFor(VERSION) + "processingJobs").put("job", "value");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getMap(namespaceFor(VERSION) + "processingJobs").get("job")).isEqualTo("value");
    }

    @Test
    void testRefusesToStartOnAStoreWrittenByANewerRelease() {
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(VERSION + 1));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> migrationService().migrateToCurrentVersion()).withMessageContaining("written by a newer release");
    }

    @Test
    void testRefusesToStartWhenTheVersionKeyIsNotAVersion() {
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set("not-a-version");

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> migrationService().migrateToCurrentVersion()).withMessageContaining("not a schema version");
    }

    @Test
    void testCarriesOverTheDeclaredStructuresAndDiscardsTheRest() {
        String old = namespaceFor(PREVIOUS_VERSION);
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(PREVIOUS_VERSION));
        redissonClient.getQueue(old + "buildResultQueue").add("result-1");
        redissonClient.getPriorityQueue(old + "buildJobQueue").add("job-1");
        redissonClient.getMap(old + "processingJobs").put("running", "agent-1");
        redissonClient.getMap(old + "features").put("Science", Boolean.FALSE);
        // Not carried over: build agents re-register on startup.
        redissonClient.getMap(old + "buildAgentInformation").put("agent-1", "details");

        migrationService().migrateToCurrentVersion();

        String current = namespaceFor(VERSION);
        assertThat(redissonClient.getQueue(current + "buildResultQueue").readAll()).containsExactly("result-1");
        assertThat(redissonClient.getPriorityQueue(current + "buildJobQueue").readAll()).containsExactly("job-1");
        assertThat(redissonClient.getMap(current + "processingJobs").get("running")).isEqualTo("agent-1");
        // A toggle an admin turned off stays off, which is the reason this map is carried over at all.
        assertThat(redissonClient.getMap(current + "features").get("Science")).isEqualTo(Boolean.FALSE);
        assertThat(redissonClient.getMap(current + "buildAgentInformation").isEmpty()).isTrue();

        assertThat(redissonClient.getKeys().getKeysByPattern(old + "*")).isEmpty();
        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
    }

    @Test
    void testLeavesTheOldVersionInPlaceUntilTheMigrationCompletes() {
        String old = namespaceFor(PREVIOUS_VERSION);
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(PREVIOUS_VERSION));
        redissonClient.getQueue(old + "buildResultQueue").add("result-1");

        // A node that dies here has moved nothing and written no version, so the store still reads as the old one and
        // a rerun starts over rather than resuming into a half-filled namespace.
        assertThat(storedVersion()).isEqualTo(String.valueOf(PREVIOUS_VERSION));

        migrationService().migrateToCurrentVersion();

        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
    }

    @Test
    void testARerunAfterAPartialDrainNeitherLosesNorDuplicatesEntries() {
        String old = namespaceFor(PREVIOUS_VERSION);
        String current = namespaceFor(VERSION);
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(PREVIOUS_VERSION));
        // Simulates a crash part way through: one entry already moved, one still waiting.
        redissonClient.getQueue(current + "buildResultQueue").add("already-moved");
        redissonClient.getQueue(old + "buildResultQueue").add("still-waiting");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(current + "buildResultQueue").readAll()).containsExactlyInAnyOrder("already-moved", "still-waiting");
        assertThat(redissonClient.getKeys().getKeysByPattern(old + "*")).isEmpty();
    }

    @Test
    void testIsIdempotent() {
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(PREVIOUS_VERSION));
        redissonClient.getQueue(namespaceFor(PREVIOUS_VERSION) + "buildResultQueue").add("result-1");

        migrationService().migrateToCurrentVersion();
        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(namespaceFor(VERSION) + "buildResultQueue").readAll()).containsExactly("result-1");
    }
}
