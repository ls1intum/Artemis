package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.RELEASE_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.UNVERSIONED;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.keyFor;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.keyPatternFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.connection.CRC16;
import org.testcontainers.DockerClientFactory;

import com.redis.testcontainers.RedisStackContainer;

/**
 * Covers the namespace migration against a real Redis, which is the only way to exercise the drain: the semantics that
 * matter here (an atomic take, a pattern delete, a version key written last) are Redis behaviour rather than ours.
 */
// requires docker for testContainers to start a test redis instance
@EnabledIf("isDockerAvailable")
class RedissonDistributedDataMigratorTest {

    /**
     * A migration between two numbered namespaces. {@link DistributedDataSchema#VERSION} still stands at its first
     * value, so the only migration the constant can express is the one out of the unversioned store; these two let the
     * numbered path be exercised as well, which is what every future bump will take.
     */
    private static final int OLD_VERSION = VERSION + 1;

    private static final int NEW_VERSION = VERSION + 2;

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
        return migrationServiceFor(VERSION);
    }

    private static RedissonDistributedDataMigrator migrationServiceFor(int targetVersion) {
        return new RedissonDistributedDataMigrator(redissonClient, "1.2.3", targetVersion);
    }

    private static String storedVersion() {
        return redissonClient.<String>getBucket(VERSION_KEY, StringCodec.INSTANCE).get();
    }

    @Test
    void testClaimsAnEmptyStoreThatHasNoVersionYet() {
        migrationService().migrateToCurrentVersion();

        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
        assertThat(redissonClient.<String>getBucket(RELEASE_KEY, StringCodec.INSTANCE).get()).isEqualTo("1.2.3");
    }

    @Test
    void testDoesNothingWhenTheStoreIsAlreadyCurrent() {
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(VERSION));
        redissonClient.getMap(keyFor(VERSION, "processingJobs")).put("job", "value");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getMap(keyFor(VERSION, "processingJobs")).get("job")).isEqualTo("value");
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
        int old = OLD_VERSION;
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(OLD_VERSION));
        redissonClient.getQueue(keyFor(old, "buildResultQueue")).add("result-1");
        redissonClient.getPriorityQueue(keyFor(old, "buildJobQueue")).add("job-1");
        redissonClient.getMap(keyFor(old, "processingJobs")).put("running", "agent-1");
        redissonClient.getMap(keyFor(old, "features")).put("Science", Boolean.FALSE);
        redissonClient.getMapCache(keyFor(old, "pyris-job-map")).put("job-1", "session-1");
        // Not carried over: build agents re-register on startup.
        redissonClient.getMap(keyFor(old, "buildAgentInformation")).put("agent-1", "details");

        migrationServiceFor(NEW_VERSION).migrateToCurrentVersion();

        int current = NEW_VERSION;
        assertThat(redissonClient.getQueue(keyFor(current, "buildResultQueue")).readAll()).containsExactly("result-1");
        assertThat(redissonClient.getPriorityQueue(keyFor(current, "buildJobQueue")).readAll()).containsExactly("job-1");
        assertThat(redissonClient.getMap(keyFor(current, "processingJobs")).get("running")).isEqualTo("agent-1");
        // A toggle an admin turned off stays off, which is the reason this map is carried over at all.
        assertThat(redissonClient.getMap(keyFor(current, "features")).get("Science")).isEqualTo(Boolean.FALSE);
        assertThat(redissonClient.getMapCache(keyFor(current, "pyris-job-map")).get("job-1")).isEqualTo("session-1");
        assertThat(redissonClient.getMap(keyFor(current, "buildAgentInformation")).isEmpty()).isTrue();

        assertThat(redissonClient.getKeys().getKeysByPattern(keyPatternFor(old))).isEmpty();
        assertThat(storedVersion()).isEqualTo(String.valueOf(NEW_VERSION));
    }

    /**
     * The migration every existing Redis deployment takes first. Nothing wrote a version key before this change, so a
     * store that predates it has to be recognised by the structures it holds rather than by what it says about itself.
     */
    @Test
    void testCarriesTheUnversionedStoreOverIntoTheFirstNamespace() {
        redissonClient.getQueue("buildResultQueue").add("result-1");
        redissonClient.getPriorityQueue("buildJobQueue").add("job-1");
        redissonClient.getMap("features").put("Science", Boolean.FALSE);

        migrationService().migrateToCurrentVersion();

        int current = VERSION;
        assertThat(redissonClient.getQueue(keyFor(current, "buildResultQueue")).readAll()).containsExactly("result-1");
        assertThat(redissonClient.getPriorityQueue(keyFor(current, "buildJobQueue")).readAll()).containsExactly("job-1");
        assertThat(redissonClient.getMap(keyFor(current, "features")).get("Science")).isEqualTo(Boolean.FALSE);
        // Drained rather than copied, so the plain keys are gone even though no pattern delete ran over them.
        assertThat(redissonClient.getQueue("buildResultQueue").isEmpty()).isTrue();
        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
    }

    /**
     * The unversioned namespace is the whole keyspace, so the pattern delete that empties a numbered one would take
     * the new namespace and the version key with it. What is not carried over is therefore left where it is.
     */
    @Test
    void testLeavesUncarriedKeysOfAnUnversionedStoreAlone() {
        redissonClient.getQueue("buildResultQueue").add("result-1");
        redissonClient.getMap("buildAgentInformation").put("agent-1", "details");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getMap("buildAgentInformation").get("agent-1")).isEqualTo("details");
        assertThat(redissonClient.getMap(keyFor(VERSION, "buildAgentInformation")).isEmpty()).isTrue();
        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
    }

    @Test
    void testAnExpiringEntryKeepsItsRemainingLifetime() {
        redissonClient.getMapCache("pyris-job-map").put("job-1", "session-1", 1, TimeUnit.HOURS);

        migrationService().migrateToCurrentVersion();

        RMapCache<Object, Object> migrated = redissonClient.getMapCache(keyFor(VERSION, "pyris-job-map"));
        assertThat(migrated.get("job-1")).isEqualTo("session-1");
        // Carried over rather than reset: an entry that was minutes from expiring must not become permanent.
        assertThat(migrated.remainTimeToLive("job-1")).isPositive().isLessThanOrEqualTo(Duration.ofHours(1).toMillis());
    }

    @Test
    void testLeavesTheOldVersionInPlaceUntilTheMigrationCompletes() {
        int old = OLD_VERSION;
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(OLD_VERSION));
        redissonClient.getQueue(keyFor(old, "buildResultQueue")).add("result-1");

        // A node that dies here has moved nothing and written no version, so the store still reads as the old one and
        // a rerun starts over rather than resuming into a half-filled namespace.
        assertThat(storedVersion()).isEqualTo(String.valueOf(OLD_VERSION));

        migrationServiceFor(NEW_VERSION).migrateToCurrentVersion();

        assertThat(storedVersion()).isEqualTo(String.valueOf(NEW_VERSION));
    }

    @Test
    void testARerunAfterAPartialDrainNeitherLosesNorDuplicatesEntries() {
        int old = OLD_VERSION;
        int current = NEW_VERSION;
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(OLD_VERSION));
        // Simulates a crash part way through: one entry already moved, one still waiting.
        redissonClient.getQueue(keyFor(current, "buildResultQueue")).add("already-moved");
        redissonClient.getQueue(keyFor(old, "buildResultQueue")).add("still-waiting");

        migrationServiceFor(NEW_VERSION).migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(keyFor(current, "buildResultQueue")).readAll()).containsExactlyInAnyOrder("already-moved", "still-waiting");
        assertThat(redissonClient.getKeys().getKeysByPattern(keyPatternFor(old))).isEmpty();
    }

    /**
     * A queue entry moves in one server-side step, so a rerun over a partly drained queue can neither lose an entry nor
     * append one twice. It matters for queues specifically: the consumers of a build job and of a build result are not
     * idempotent, so unlike a map or a set they cannot absorb a repeat.
     */
    @Test
    void testARerunOverAPartlyDrainedQueueDoesNotDuplicateEntries() {
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(OLD_VERSION));
        redissonClient.getQueue(keyFor(OLD_VERSION, "buildResultQueue")).addAll(List.of("first", "second", "third"));

        // Drains "first" and "second" the way the migrator does, then stops as if the node had died.
        var partiallyDrained = redissonClient.getQueue(keyFor(OLD_VERSION, "buildResultQueue"), ByteArrayCodec.INSTANCE);
        String targetKey = keyFor(NEW_VERSION, "buildResultQueue");
        assertThat(partiallyDrained.pollLastAndOfferFirstTo(targetKey)).isNotNull();
        assertThat(partiallyDrained.pollLastAndOfferFirstTo(targetKey)).isNotNull();

        migrationServiceFor(NEW_VERSION).migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(targetKey).readAll()).as("every entry exactly once, in the order the source held").containsExactly("first", "second", "third");
    }

    /**
     * Both namespaces of a structure have to land in the same Redis Cluster slot, which is what lets the drain move an
     * entry in one step. A plain unversioned key hashes over its whole name, so the versioned key has to carry that
     * same name as its hash tag.
     */
    @Test
    void testEveryVersionOfAStructureSharesOneClusterSlot() {
        assertThat(clusterSlotOf(keyFor(UNVERSIONED, "buildJobQueue"))).isEqualTo(clusterSlotOf(keyFor(VERSION, "buildJobQueue")))
                .isEqualTo(clusterSlotOf(keyFor(NEW_VERSION, "buildJobQueue")));
        assertThat(clusterSlotOf(keyFor(VERSION, "buildJobQueue"))).as("different structures still hash apart").isNotEqualTo(clusterSlotOf(keyFor(VERSION, "buildResultQueue")));
        // The notification topic of a queue has to travel with it, or a cluster splits the two across nodes.
        assertThat(clusterSlotOf(keyFor(VERSION, "buildJobQueue") + ":queue_notification")).isEqualTo(clusterSlotOf(keyFor(VERSION, "buildJobQueue")));
    }

    /**
     * The Redis Cluster key-hashing rule: when a key contains a non-empty {@code {...}}, only what is inside decides
     * the slot, otherwise the whole key does. Written out rather than taken from Redisson, so the assertion is against
     * the specification the cluster follows.
     *
     * @param key the Redis key
     * @return the hash slot it lands in
     */
    private static int clusterSlotOf(String key) {
        int start = key.indexOf('{');
        int end = start < 0 ? -1 : key.indexOf('}', start + 1);
        String hashed = end > start + 1 ? key.substring(start + 1, end) : key;
        return CRC16.crc16(hashed.getBytes(StandardCharsets.UTF_8)) % 16384;
    }

    /**
     * A map entry is written to the target before it is removed from the source, so a node that dies between the two
     * leaves a duplicate rather than nothing. The rerun has to overwrite it rather than add a second one.
     */
    @Test
    void testARerunOverAnAlreadyMovedMapEntryDoesNotDuplicateIt() {
        int old = OLD_VERSION;
        int current = NEW_VERSION;
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(OLD_VERSION));
        redissonClient.getMap(keyFor(old, "processingJobs")).put("running", "agent-1");
        redissonClient.getMap(keyFor(current, "processingJobs")).put("running", "agent-1");

        migrationServiceFor(NEW_VERSION).migrateToCurrentVersion();

        assertThat(redissonClient.getMap(keyFor(current, "processingJobs"))).hasSize(1).containsEntry("running", "agent-1");
    }

    @Test
    void testIsIdempotent() {
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(OLD_VERSION));
        redissonClient.getQueue(keyFor(OLD_VERSION, "buildResultQueue")).add("result-1");

        migrationServiceFor(NEW_VERSION).migrateToCurrentVersion();
        migrationServiceFor(NEW_VERSION).migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(keyFor(NEW_VERSION, "buildResultQueue")).readAll()).containsExactly("result-1");
    }
}
