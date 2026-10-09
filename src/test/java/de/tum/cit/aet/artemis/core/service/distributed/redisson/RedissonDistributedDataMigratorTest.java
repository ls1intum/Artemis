package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.RELEASE_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.UNVERSIONED;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.keyFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.connection.CRC16;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;

import com.redis.testcontainers.RedisContainer;

import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.CarriedOverStructure;
import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.StructureKind;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

/**
 * Covers the namespace migration against a real Redis, which is the only way to exercise the drain: the semantics that
 * matter here (an atomic take, a pattern delete, a version key written last) are Redis behaviour rather than ours.
 */
// requires docker for testContainers to start a test redis instance
@EnabledIf("isDockerAvailable")
class RedissonDistributedDataMigratorTest {

    private static RedisContainer valkey;

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
        valkey = ValkeyTestContainerFactory.create();
        valkey.start();
        Config config = new Config();
        // The same codec the provider installs, so values written here round-trip exactly as production ones do.
        config.setCodec(new BackwardCompatibleSerializationCodec());
        config.useSingleServer().setAddress("redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379));
        redissonClient = Redisson.create(config);
    }

    @AfterAll
    static void afterAll() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        if (valkey != null) {
            valkey.stop();
        }
    }

    @BeforeEach
    void clearStore() {
        redissonClient.getKeys().flushall();
    }

    private static RedissonDistributedDataMigrator migrationService() {
        return new RedissonDistributedDataMigrator(redissonClient, "1.2.3");
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

    /**
     * The migration every existing Redis deployment takes first. Nothing wrote a version key before this change, so a
     * store that predates it has to be recognised by the structures it holds rather than by what it says about itself.
     */
    @Test
    void testCarriesTheUnversionedStoreOverIntoTheFirstNamespace() {
        redissonClient.getQueue("buildResultQueue").add("result-1");
        redissonClient.getPriorityQueue("buildJobQueue").add("job-1");
        redissonClient.getMap("processingJobs").put("running", "agent-1");
        redissonClient.getMap("features").put(Feature.Science, Boolean.FALSE);

        migrationService().migrateToCurrentVersion();

        int current = VERSION;
        assertThat(redissonClient.getQueue(keyFor(current, "buildResultQueue")).readAll()).containsExactly("result-1");
        assertThat(redissonClient.getPriorityQueue(keyFor(current, "buildJobQueue")).readAll()).containsExactly("job-1");
        assertThat(redissonClient.getMap(keyFor(current, "processingJobs"))).containsEntry("running", "agent-1");
        assertThat(redissonClient.getMap(keyFor(current, "features")).get(Feature.Science)).isEqualTo(Boolean.FALSE);
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
    void testARerunAfterAPartialDrainNeitherLosesNorDuplicatesEntries() {
        int current = VERSION;
        // Simulates a crash part way through: one entry already moved, one still waiting.
        redissonClient.getQueue(keyFor(current, "buildResultQueue")).add("already-moved");
        redissonClient.getQueue(keyFor(UNVERSIONED, "buildResultQueue")).add("still-waiting");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(keyFor(current, "buildResultQueue")).readAll()).containsExactlyInAnyOrder("already-moved", "still-waiting");
        assertThat(redissonClient.getQueue(keyFor(UNVERSIONED, "buildResultQueue"))).isEmpty();
    }

    /**
     * A queue entry moves in one server-side step, so a rerun over a partly drained queue can neither lose an entry nor
     * append one twice. It matters for queues specifically: the consumers of a build job and of a build result are not
     * idempotent, so unlike a map or a set they cannot absorb a repeat.
     */
    @Test
    void testARerunOverAPartlyDrainedQueueDoesNotDuplicateEntries() {
        redissonClient.getQueue(keyFor(UNVERSIONED, "buildResultQueue")).addAll(List.of("first", "second", "third"));

        // Drains "first" and "second" the way the migrator does, then stops as if the node had died.
        var partiallyDrained = redissonClient.getQueue(keyFor(UNVERSIONED, "buildResultQueue"), ByteArrayCodec.INSTANCE);
        String targetKey = keyFor(VERSION, "buildResultQueue");
        assertThat(partiallyDrained.pollLastAndOfferFirstTo(targetKey)).isNotNull();
        assertThat(partiallyDrained.pollLastAndOfferFirstTo(targetKey)).isNotNull();

        migrationService().migrateToCurrentVersion();

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
                .isEqualTo(clusterSlotOf(keyFor(VERSION + 1, "buildJobQueue")));
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
        int current = VERSION;
        redissonClient.getMap(keyFor(UNVERSIONED, "processingJobs")).put("running", "agent-1");
        redissonClient.getMap(keyFor(current, "processingJobs")).put("running", "agent-1");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getMap(keyFor(current, "processingJobs"))).hasSize(1).containsEntry("running", "agent-1");
    }

    @Test
    void testIsIdempotent() {
        redissonClient.getQueue(keyFor(UNVERSIONED, "buildResultQueue")).add("result-1");

        migrationService().migrateToCurrentVersion();
        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(keyFor(VERSION, "buildResultQueue")).readAll()).containsExactly("result-1");
    }

    @Test
    void testRefusesToSkipAMissingAdjacentMigration() {
        redissonClient.getQueue(keyFor(UNVERSIONED, "buildResultQueue")).add("must-remain-unversioned");

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> migrationServiceFor(VERSION + 1).migrateToCurrentVersion())
                .withMessageContaining("no migration step from " + VERSION).withMessageContaining("explicit adjacent-version migration");
        assertThat(storedVersion()).isNull();
        assertThat(redissonClient.getQueue(keyFor(UNVERSIONED, "buildResultQueue")).readAll()).containsExactly("must-remain-unversioned");
        assertThat(redissonClient.getQueue(keyFor(VERSION, "buildResultQueue"))).isEmpty();
        assertThat(redissonClient.getQueue(keyFor(VERSION + 1, "buildResultQueue"))).isEmpty();
    }

    /**
     * No set is carried over by the unversioned-to-first migration, so the set drain is reached through the private
     * drain step. More members than one batch hold, so the iteration has to restart and finish what the first pass left.
     */
    @Test
    void testDrainsASetLargerThanOneBatchWithoutLosingMembers() {
        int memberCount = 2500;
        RSet<byte[]> source = redissonClient.getSet(keyFor(UNVERSIONED, "someSet"), ByteArrayCodec.INSTANCE);
        List<byte[]> members = new ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            members.add(("member-" + i).getBytes(StandardCharsets.UTF_8));
        }
        source.addAll(members);

        Long moved = ReflectionTestUtils.invokeMethod(migrationService(), "drain", UNVERSIONED, VERSION, new CarriedOverStructure("someSet", StructureKind.SET));

        assertThat(moved).isEqualTo(memberCount);
        assertThat(source.isEmpty()).isTrue();
        RSet<String> target = redissonClient.getSet(keyFor(VERSION, "someSet"), StringCodec.INSTANCE);
        assertThat(target.size()).isEqualTo(memberCount);
        assertThat(target.contains("member-0")).isTrue();
        assertThat(target.contains("member-" + (memberCount - 1))).isTrue();
    }

    @Test
    void testDrainingAnEmptySetMovesNothing() {
        Long moved = ReflectionTestUtils.invokeMethod(migrationService(), "drain", UNVERSIONED, VERSION, new CarriedOverStructure("emptySet", StructureKind.SET));

        assertThat(moved).isZero();
    }

    /**
     * How schema version 1 encoded a {@link Feature} map key, captured from the build that still had {@code AtlasML}:
     * the class name, then the constant's position plus one. Golden bytes rather than an encoding computed here, because
     * the current enum can no longer produce them.
     */
    private static final String V1_FEATURE_KEY_PREFIX = "010064652e74756d2e6369742e6165742e617274656d69732e636f72652e736572766963652e666561747572652e466561747572e5";

    private static byte[] v1FeatureKey(int v1Position) {
        return HexFormat.of().parseHex(V1_FEATURE_KEY_PREFIX + HexFormat.of().toHexDigits((byte) (v1Position + 1)));
    }

    private static byte[] encodedToggle(boolean enabled) throws Exception {
        ByteBuf encoded = new BackwardCompatibleSerializationCodec().getMapValueEncoder().encode(enabled);
        try {
            return ByteBufUtil.getBytes(encoded);
        }
        finally {
            encoded.release();
        }
    }

    private static RMap<byte[], byte[]> rawFeatures(int version) {
        return redissonClient.getMap(keyFor(version, "features"), ByteArrayCodec.INSTANCE);
    }

    private static void claimVersion(int version) {
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(version));
    }

    /**
     * Removing {@code AtlasML} moved every later constant one position up. A v1 AtlasAgent key read with the current enum
     * would be Memiris, so the migration must resolve each key against the v1 order, not copy its bytes.
     */
    @Test
    void testV1ToV2KeepsEveryFeatureToggleUnderItsOwnName() throws Exception {
        claimVersion(1);
        RMap<byte[], byte[]> v1 = rawFeatures(1);
        v1.put(v1FeatureKey(4), encodedToggle(false));  // Science, before AtlasML
        v1.put(v1FeatureKey(7), encodedToggle(true));   // AtlasML, removed in v2
        v1.put(v1FeatureKey(8), encodedToggle(true));   // AtlasAgent, now one position up
        v1.put(v1FeatureKey(9), encodedToggle(false));  // Memiris
        v1.put(v1FeatureKey(17), encodedToggle(true));  // GlobalSearchReconcileOrphan, the last constant

        migrationService().migrateToCurrentVersion();

        RMap<Feature, Boolean> features = redissonClient.getMap(keyFor(2, "features"));
        assertThat(features.readAllMap()).containsOnly(entry(Feature.Science, false), entry(Feature.AtlasAgent, true), entry(Feature.Memiris, false),
                entry(Feature.GlobalSearchReconcileOrphan, true));
        assertThat(rawFeatures(1).isExists()).isFalse();
        assertThat(storedVersion()).isEqualTo("2");
    }

    @Test
    void testV1ToV2MovesTheOtherStructuresUnchanged() {
        claimVersion(1);
        redissonClient.getQueue(keyFor(1, "buildResultQueue")).add("result-1");
        redissonClient.getPriorityQueue(keyFor(1, "buildJobQueue")).add("job-1");
        redissonClient.getMap(keyFor(1, "processingJobs")).put("running", "agent-1");
        redissonClient.getMapCache(keyFor(1, "pyris-job-map")).put("job-1", "session-1", 1, TimeUnit.HOURS);
        redissonClient.getMap(keyFor(1, "buildAgentInformation")).put("agent-1", "details");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(keyFor(2, "buildResultQueue")).readAll()).containsExactly("result-1");
        assertThat(redissonClient.getPriorityQueue(keyFor(2, "buildJobQueue")).readAll()).containsExactly("job-1");
        assertThat(redissonClient.getMap(keyFor(2, "processingJobs"))).containsEntry("running", "agent-1");
        assertThat(redissonClient.getMapCache(keyFor(2, "pyris-job-map")).get("job-1")).isEqualTo("session-1");
        // Not carried over, and the whole v1 namespace is deleted once the step completes.
        assertThat(redissonClient.getMap(keyFor(2, "buildAgentInformation")).isEmpty()).isTrue();
        assertThat(redissonClient.getKeys().getKeysByPattern("artemis:v1:*")).isEmpty();
    }

    /**
     * A toggle is written to v2 before it is removed from v1, so a crash in between leaves it in both. The rerun has to
     * overwrite the v2 entry, not add a second one under another encoding.
     */
    @Test
    void testV1ToV2RerunAfterAPartialFeatureMoveDoesNotDuplicateToggles() throws Exception {
        claimVersion(1);
        rawFeatures(1).put(v1FeatureKey(8), encodedToggle(true));
        redissonClient.getMap(keyFor(2, "features")).put(Feature.AtlasAgent, Boolean.TRUE);

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.<Feature, Boolean>getMap(keyFor(2, "features")).readAllMap()).containsOnly(entry(Feature.AtlasAgent, true));
    }

    /**
     * An unreadable toggle must not keep the instance from starting: it is dropped and startup seeds its default again.
     */
    @Test
    void testV1ToV2DropsAFeatureToggleItCannotRead() throws Exception {
        claimVersion(1);
        rawFeatures(1).put("not-a-feature".getBytes(StandardCharsets.UTF_8), encodedToggle(true));
        rawFeatures(1).put(v1FeatureKey(0), encodedToggle(true));

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.<Feature, Boolean>getMap(keyFor(2, "features")).readAllMap()).containsOnly(entry(Feature.ProgrammingExercises, true));
        assertThat(storedVersion()).isEqualTo("2");
    }
}
