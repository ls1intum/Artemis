package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.LEGACY_VERSION;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.RELEASE_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.keyFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.Kryo5Codec;
import org.redisson.config.Config;
import org.testcontainers.DockerClientFactory;

import com.redis.testcontainers.RedisStackContainer;

/**
 * Covers migration against a real Redis. The scripts, source/target ordering, map-cache expiry metadata and version key
 * ordering are Redis behavior and cannot be verified meaningfully with mocks.
 */
// requires docker for testContainers to start a test redis instance
@EnabledIf("isDockerAvailable")
class RedissonDistributedDataMigratorTest {

    private static RedissonClient redissonClient;

    private static RedisStackContainer redis;

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
    void testClaimsAConfirmedEmptyLegacyStore() {
        migrationService().migrateToCurrentVersion();

        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
        assertThat(redissonClient.<String>getBucket(RELEASE_KEY, StringCodec.INSTANCE).get()).isEqualTo("1.2.3");
        assertThat(redissonClient.getKeys().countExists(keyFor(VERSION, "buildJobQueue"), keyFor(VERSION, "processingJobs"), keyFor(VERSION, "buildResultQueue"),
                keyFor(VERSION, "features"), keyFor(VERSION, "pyris-job-map"))).isZero();
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
    void testMigratesLegacyStructuresPreservesExpiryAndLeavesUnrelatedKeysUntouched() {
        redissonClient.getQueue(keyFor(LEGACY_VERSION, "buildResultQueue")).add("result-1");
        redissonClient.getPriorityQueue(keyFor(LEGACY_VERSION, "buildJobQueue")).addAll(java.util.List.of("job-1", "job-3"));
        // Before BackwardCompatibleSerializationCodec, map values were also Kryo encoded. The migration must read and
        // remove those values even though current writes use Java serialization.
        redissonClient.getMap(keyFor(LEGACY_VERSION, "processingJobs"), new Kryo5Codec()).put("running", "agent-1");
        redissonClient.getMap(keyFor(LEGACY_VERSION, "features"), new Kryo5Codec()).put("Science", Boolean.FALSE);
        redissonClient.getMapCache(keyFor(LEGACY_VERSION, "pyris-job-map"), new Kryo5Codec()).put("token", "pyris-job", 10, TimeUnit.MINUTES);
        redissonClient.getMap("buildAgentInformation").put("agent-1", "re-registers");
        redissonClient.getBucket("other-application-key", StringCodec.INSTANCE).set("must-survive");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(keyFor(VERSION, "buildResultQueue")).readAll()).containsExactly("result-1");
        var migratedPriorityQueue = redissonClient.<String>getPriorityQueue(keyFor(VERSION, "buildJobQueue"));
        migratedPriorityQueue.addAll(java.util.List.of("job-2", "job-0"));
        assertThat(migratedPriorityQueue.readAll()).containsExactly("job-0", "job-1", "job-2", "job-3");
        assertThat(redissonClient.getMap(keyFor(VERSION, "processingJobs"))).containsEntry("running", "agent-1");
        assertThat(redissonClient.getMap(keyFor(VERSION, "features"))).containsEntry("Science", Boolean.FALSE);
        assertThat(redissonClient.getMapCache(keyFor(VERSION, "pyris-job-map"))).containsEntry("token", "pyris-job");
        assertThat(redissonClient.getMapCache(keyFor(VERSION, "pyris-job-map")).remainTimeToLive("token")).isBetween(TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(10));

        assertThat(redissonClient.getQueue("buildResultQueue")).isEmpty();
        assertThat(redissonClient.getPriorityQueue("buildJobQueue")).isEmpty();
        assertThat(redissonClient.getMap("processingJobs")).isEmpty();
        assertThat(redissonClient.getMap("features")).isEmpty();
        assertThat(redissonClient.getMapCache("pyris-job-map")).isEmpty();
        assertThat(redissonClient.getMap(keyFor(VERSION, "buildAgentInformation"))).isEmpty();
        assertThat(redissonClient.getMap("buildAgentInformation")).containsEntry("agent-1", "re-registers");
        assertThat(redissonClient.<String>getBucket("other-application-key", StringCodec.INSTANCE).get()).isEqualTo("must-survive");
        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
    }

    @Test
    void testExplicitLegacyVersionMarkerUsesTheSameMigrationStep() {
        redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE).set(String.valueOf(LEGACY_VERSION));
        redissonClient.getQueue("buildResultQueue").add("result-1");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(keyFor(VERSION, "buildResultQueue")).readAll()).containsExactly("result-1");
        assertThat(redissonClient.getQueue("buildResultQueue")).isEmpty();
        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
    }

    @Test
    void testResumesAfterTargetQueueWriteWithoutDuplicatingTheEntry() {
        String source = keyFor(LEGACY_VERSION, "buildResultQueue");
        String target = keyFor(VERSION, "buildResultQueue");
        String marker = RedissonDistributedDataMigrator.queueMigrationMarkerKey(target);
        redissonClient.getQueue(source).add("in-flight");
        // State left by a process that completed the atomic target write but stopped before removing the source.
        redissonClient.getQueue(target).add("in-flight");
        redissonClient.getSet(marker).add("in-flight");

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getQueue(source)).isEmpty();
        assertThat(redissonClient.getQueue(target).readAll()).containsExactly("in-flight");
        assertThat(redissonClient.getSet(marker)).isEmpty();
        assertThat(storedVersion()).isEqualTo(String.valueOf(VERSION));
    }

    @Test
    void testRerunOverwritesAlreadyCopiedMapValueAndRemovesTheSource() {
        String source = keyFor(LEGACY_VERSION, "features");
        String target = keyFor(VERSION, "features");
        redissonClient.getMap(source).put("Science", Boolean.FALSE);
        redissonClient.getMap(target).put("Science", Boolean.TRUE);

        migrationService().migrateToCurrentVersion();

        assertThat(redissonClient.getMap(source)).isEmpty();
        assertThat(redissonClient.getMap(target)).containsOnlyKeys("Science").containsEntry("Science", Boolean.FALSE);
    }
}
