package de.tum.cit.aet.artemis.shared;

import org.testcontainers.utility.DockerImageName;

import com.redis.testcontainers.RedisContainer;

/**
 * Builds the Valkey container the Redis-backed distributed data tests run against.
 *
 * <p>
 * Artemis reaches the store through {@code DistributedDataProvider} with
 * {@code artemis.distributed-data.provider: Redis}, and Valkey implements the Redis protocol, so these tests exercise
 * exactly the production code path. Valkey is what the repository pins and tests because it is BSD-3-Clause; a
 * deployment may run Redis 8 instead, which is protocol-compatible.
 *
 * <p>
 * The version comes from {@code VALKEY_VERSION} in {@code .env}, which {@code gradle/test.gradle} hands to the test
 * JVM as the {@code valkey.version} system property. Keeping the single source of truth there is what stops the test
 * pin from drifting away from the pin the E2E stacks use - the drift that left these tests on
 * {@code redis-stack-server:7.4.0-v8} while the rest of the world moved on.
 *
 * @see de.tum.cit.aet.artemis.core.service.distributed.RedissonDistributedDataTest
 * @see de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataMigratorTest
 * @see de.tum.cit.aet.artemis.core.service.distributed.RedissonPriorityQueueBenchmarkTest
 */
public final class ValkeyTestContainerFactory {

    private static final String VERSION_PROPERTY = "valkey.version";

    private ValkeyTestContainerFactory() {
    }

    /**
     * Creates an unstarted container on the pinned Valkey image. Each caller owns its own lifecycle, because these
     * tests deliberately start from an empty store.
     *
     * @return the container, still to be started by the caller
     * @throws IllegalStateException if the {@code valkey.version} system property is missing, which means the test is
     *                                   running outside Gradle; failing loudly beats falling back to a hardcoded
     *                                   version that can drift away from the pin the compose files use
     */
    public static RedisContainer create() {
        String version = System.getProperty(VERSION_PROPERTY);
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("The '" + VERSION_PROPERTY + "' system property is not set. It is derived from VALKEY_VERSION in .env by gradle/test.gradle, "
                    + "so run this test through Gradle, or pass -D" + VERSION_PROPERTY + "=<version> explicitly.");
        }
        // A no-op today: RedisContainer asserts nothing about the image name, it only exposes 6379 and waits for the
        // "Ready to accept connections" log line, which Valkey emits too. Declared anyway so the intent survives if
        // the library ever does start checking.
        return new RedisContainer(DockerImageName.parse("valkey/valkey:" + version).asCompatibleSubstituteFor("redis"));
    }
}
