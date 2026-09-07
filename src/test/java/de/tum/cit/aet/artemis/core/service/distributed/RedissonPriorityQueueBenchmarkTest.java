package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

import com.redis.testcontainers.RedisStackContainer;

import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedQueue;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Measures how the cost of enqueueing a build job into a Redis-backed queue scales with the queue depth.
 *
 * <p>
 * <strong>Why this exists.</strong> {@code DistributedDataAccessService#getDistributedBuildJobQueue} uses
 * {@code getPriorityQueue}, which Redisson implements as {@code RedissonPriorityQueue}. Each {@code add} acquires a
 * cluster-wide {@code RLock} named {@code redisson_sortedset_lock:<queue>}, then performs a <em>client-side</em> binary
 * search: one {@code LLEN} plus up to log2(n) individual {@code LINDEX} round trips, each deserializing a full
 * {@link BuildJobQueueItem}, before an {@code EVAL} that runs {@code LINSERT}. Enqueues therefore serialize
 * cluster-wide and get more expensive as the queue grows. This benchmark decides whether that is worth replacing with
 * a Redis sorted set ({@code ZADD}/{@code ZPOPMIN}), which is atomic, server-side and O(log n) without a lock.
 *
 * <p>
 * <strong>Design.</strong> The control is the plain queue from {@code getQueue} (a single {@code RPUSH}), measured
 * against the same container, network, codec and notification topic. Comparing the two isolates the cost of priority
 * insertion itself. Comparing against Hazelcast instead would be confounded, because Hazelcast only orders the queue
 * literally named {@code buildJobQueue} and a live {@code SharedQueueProcessingService} consumes that queue in tests,
 * which would change the queue depth during measurement.
 *
 * <p>
 * Opt-in, because it enqueues thousands of items and is far too slow for CI:
 *
 * <pre>
 * ./gradlew test --tests "RedissonPriorityQueueBenchmarkTest" -Dartemis.benchmark=true -x webapp
 * </pre>
 */
@EnabledIfSystemProperty(named = "artemis.benchmark", matches = "true")
class RedissonPriorityQueueBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(RedissonPriorityQueueBenchmarkTest.class);

    /**
     * Queue depths to sample. 5000 is chosen to represent an exam-start burst.
     */
    private static final int[] DEPTHS = { 100, 1000, 5000 };

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
        assertThat(isDockerAvailable()).as("this benchmark requires Docker for the Redis testcontainer").isTrue();
        redis = new RedisStackContainer(RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));
        redis.start();

        Config config = new Config();
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

    /**
     * Builds a realistically sized build job. Payload size matters here: the binary search deserializes a whole item
     * per probe, so a toy payload would understate the cost.
     *
     * @param priority the build job priority
     * @return a build job queue item
     */
    private static BuildJobQueueItem buildJob(int priority) {
        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), null, null, null, 15);
        RepositoryInfo repositoryInfo = new RepositoryInfo("dummy-repo-slug", RepositoryType.USER, RepositoryType.USER,
                "https://artemis.tum.de/git/project/project-assignmentDummySlug.git", "https://artemis.tum.de/git/project/project-testDummySlug.git",
                "https://artemis.tum.de/git/project/project-solutionDummySlug.git", new String[] {}, new String[] {});
        BuildConfig buildConfig = new BuildConfig("dummy-build-script", "dummy-docker-image", "dummy-commit-hash", "assignment-commit-hash", "test-commit-hash", "main",
                ProgrammingLanguage.JAVA, ProjectType.MAVEN_MAVEN, false, false, List.of("dummy-result-path"), 15, "dummy-assignment-checkout-path", "dummy-test-checkout-path",
                "dummy-solution-checkout-path", null);
        return new BuildJobQueueItem("dummy-id-" + UUID.randomUUID(), "dummy-name", null, 1, 1, 1, 0, priority, null, repositoryInfo, jobTimingInfo, buildConfig, null);
    }

    private DistributedQueue<BuildJobQueueItem> priorityQueue(String name) {
        return new RedissonDistributedQueue<>(redissonClient.getPriorityQueue(name), redissonClient.getTopic(name + ":queue_notification"), name);
    }

    private DistributedQueue<BuildJobQueueItem> plainQueue(String name) {
        return new RedissonDistributedQueue<>(redissonClient.getQueue(name), redissonClient.getTopic(name + ":queue_notification"), name);
    }

    /**
     * Enqueues {@code depth} items and returns the per-add durations in microseconds.
     *
     * @param queue the queue to fill
     * @param depth how many items to enqueue
     * @return per-add durations in microseconds, in insertion order
     */
    private static long[] measureEnqueue(DistributedQueue<BuildJobQueueItem> queue, int depth) {
        long[] durationsMicros = new long[depth];
        for (int i = 0; i < depth; i++) {
            // Interleave priorities so insertions land mid-list rather than always at the tail, which is what the
            // binary search actually has to cope with in production.
            BuildJobQueueItem item = buildJob(i % 7);
            long start = System.nanoTime();
            queue.add(item);
            durationsMicros[i] = (System.nanoTime() - start) / 1_000;
        }
        return durationsMicros;
    }

    private static long meanMicros(long[] values) {
        long total = 0;
        for (long value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static long percentileMicros(long[] values, double percentile) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int index = (int) Math.min(sorted.length - 1L, Math.round(percentile / 100.0 * sorted.length));
        return sorted[index];
    }

    /**
     * Reports the mean and p95 per-add cost, plus the cost of the tail of the run (the last 10% of inserts, when the
     * queue is at full depth), which is where any growth with queue size shows up most clearly.
     *
     * @param label           label for the measured configuration
     * @param depth           the queue depth reached
     * @param durationsMicros per-add durations
     * @return a formatted single-line summary
     */
    private static String summarise(String label, int depth, long[] durationsMicros) {
        long[] tail = java.util.Arrays.copyOfRange(durationsMicros, (int) (durationsMicros.length * 0.9), durationsMicros.length);
        long totalMillis = 0;
        for (long value : durationsMicros) {
            totalMillis += value;
        }
        return String.format("%-18s depth=%-5d mean=%5dus p95=%6dus lastDecileMean=%6dus totalWallClock=%6dms", label, depth, meanMicros(durationsMicros),
                percentileMicros(durationsMicros, 95), meanMicros(tail), totalMillis / 1000);
    }

    /**
     * Exercises both queue types before measuring. Without this, JIT compilation and Redisson connection-pool setup
     * land entirely in the first sampled depth and make it look slower than deeper runs, which is physically backwards.
     */
    private void warmUp() {
        DistributedQueue<BuildJobQueueItem> warmupPlain = plainQueue("benchmarkWarmupPlain");
        DistributedQueue<BuildJobQueueItem> warmupPriority = priorityQueue("benchmarkWarmupPriority");
        measureEnqueue(warmupPlain, 200);
        measureEnqueue(warmupPriority, 200);
        warmupPlain.clear();
        warmupPriority.clear();
    }

    @Test
    void benchmarkPriorityVersusPlainEnqueue() {
        List<String> report = new ArrayList<>();
        warmUp();

        for (int depth : DEPTHS) {
            DistributedQueue<BuildJobQueueItem> plain = plainQueue("benchmarkPlain" + depth);
            plain.clear();
            report.add(summarise("plain (RPUSH)", depth, measureEnqueue(plain, depth)));
            plain.clear();

            DistributedQueue<BuildJobQueueItem> priority = priorityQueue("benchmarkPriority" + depth);
            priority.clear();
            long[] priorityDurations = measureEnqueue(priority, depth);
            report.add(summarise("priority (LINSERT)", depth, priorityDurations));

            // getQueuedJobs() reads the whole queue on every queue mutation for the admin websocket, so record it too.
            long readStart = System.nanoTime();
            int readSize = priority.getAll().size();
            report.add(String.format("%-18s depth=%-5d getAll=%6dms (returned %d)", "priority getAll", depth, (System.nanoTime() - readStart) / 1_000_000, readSize));
            priority.clear();
        }

        log.info("=== Redis enqueue benchmark ===\n{}", String.join("\n", report));
        // Fail loudly rather than silently reporting nothing if the harness stopped measuring.
        assertThat(report).hasSize(DEPTHS.length * 3);
    }
}
