package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;

/**
 * Orders integration-test classes into "waves" of buckets so that, together with a bounded
 * {@code spring.test.context.cache.maxSize}, the Spring contexts of finished buckets can be evicted before the next
 * wave starts. This caps the peak number of concurrently cached contexts ("server starts") to roughly the wave size,
 * which keeps memory bounded on smaller CI machines while still running a full wave of buckets in parallel on larger
 * machines.
 * <p>
 * The wave size defaults to the number of available processors, so:
 * <ul>
 * <li>on a machine with at least as many cores as buckets, all buckets land in a single wave (today's behaviour), and</li>
 * <li>on a 4-core runner the buckets are processed in waves of four, so at most ~4 contexts are cached at once.</li>
 * </ul>
 * <p>
 * <b>Prototype.</b> Enabled via {@code -Dartemis.test.wave-ordering.enabled=true} (pair it with
 * {@code -Dspring.test.context.cache.maxSize=<waveSize>} to actually evict finished waves). When disabled it falls back
 * to deterministic class-name ordering, preserving the previous behaviour. Caveat: JUnit has no cross-class barrier, so
 * a wave boundary can still occasionally reload a context — validate on the target machines before enabling by default.
 */
public class BucketWaveClassOrderer implements ClassOrderer {

    private static final boolean ENABLED = Boolean.getBoolean("artemis.test.wave-ordering.enabled");

    private static final int WAVE_SIZE = Math.max(1, Integer.getInteger("artemis.test.wave-size", Runtime.getRuntime().availableProcessors()));

    // Sorts after all real bucket names so non-bucketed classes (plain unit/architecture tests) run last.
    private static final String NO_BUCKET = "~~no-bucket";

    @Override
    public void orderClasses(ClassOrdererContext context) {
        List<? extends ClassDescriptor> classDescriptors = context.getClassDescriptors();
        if (!ENABLED) {
            classDescriptors.sort(Comparator.comparing(classDescriptor -> classDescriptor.getTestClass().getName()));
            return;
        }

        // Assign each distinct bucket (sorted for stability) to a wave of WAVE_SIZE buckets.
        List<String> orderedBuckets = classDescriptors.stream().map(BucketWaveClassOrderer::bucketOf).distinct().sorted().toList();
        Map<String, Integer> waveByBucket = IntStream.range(0, orderedBuckets.size()).boxed().collect(Collectors.toMap(orderedBuckets::get, index -> index / WAVE_SIZE));

        classDescriptors.sort(Comparator.comparingInt((ClassDescriptor classDescriptor) -> waveByBucket.getOrDefault(bucketOf(classDescriptor), Integer.MAX_VALUE))
                .thenComparing(BucketWaveClassOrderer::bucketOf).thenComparing(classDescriptor -> classDescriptor.getTestClass().getName()));
    }

    private static String bucketOf(ClassDescriptor classDescriptor) {
        return TestBucketTiming.bucketForClass(classDescriptor.getTestClass()).orElse(NO_BUCKET);
    }
}
