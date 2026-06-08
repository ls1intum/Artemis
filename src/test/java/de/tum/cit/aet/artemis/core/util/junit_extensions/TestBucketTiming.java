package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import de.tum.cit.aet.artemis.core.util.junit_parallel_logging.ParallelConsoleAppender;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCBatchTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTemplateTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Aggregates wall-clock timing for the server integration-test buckets.
 * <p>
 * The summary is printed by {@link GlobalCleanupListener} after the complete JUnit test plan has finished.
 */
public final class TestBucketTiming {

    private static final int SLOW_CLASS_COUNT = 5;

    private static final List<BucketDefinition> BUCKET_DEFINITIONS = List.of(new BucketDefinition("BucketIndependent", List.of(AbstractSpringIntegrationIndependentTest.class)),
            new BucketDefinition("BucketLocalCILocalVC", List.of(AbstractSpringIntegrationLocalCILocalVCTest.class)),
            new BucketDefinition("BucketJenkinsLocalVC", List.of(AbstractSpringIntegrationJenkinsLocalVCTest.class)),
            new BucketDefinition("BucketJenkinsLocalVCBatch", List.of(AbstractSpringIntegrationJenkinsLocalVCBatchTest.class)),
            new BucketDefinition("BucketIndependentBatch", List.of(AbstractSpringIntegrationIndependentBatchTest.class)),
            new BucketDefinition("BucketSmall", List.of(AbstractSpringIntegrationLocalVCSamlTest.class, AbstractArtemisBuildAgentTest.class)),
            new BucketDefinition("BucketJenkinsLocalVCTemplate", List.of(AbstractSpringIntegrationJenkinsLocalVCTemplateTest.class)));

    private static final Set<String> BUCKET_NAMES = BUCKET_DEFINITIONS.stream().map(BucketDefinition::name).collect(Collectors.toUnmodifiableSet());

    private static final ConcurrentMap<String, BucketTiming> BUCKET_TIMINGS = new ConcurrentHashMap<>();

    private static final ConcurrentMap<String, ClassStart> ACTIVE_CLASSES = new ConcurrentHashMap<>();

    private static final Set<String> COUNTED_TESTS = ConcurrentHashMap.newKeySet();

    private TestBucketTiming() {
    }

    static void recordExecutionStarted(TestIdentifier testIdentifier) {
        if (!isClassContainer(testIdentifier)) {
            return;
        }

        Optional<Class<?>> testClass = getJavaClass(testIdentifier);
        Optional<String> bucketName = findBucketName(testIdentifier, testClass);
        if (testClass.isEmpty() || bucketName.isEmpty()) {
            return;
        }

        long startNanos = System.nanoTime();
        BUCKET_TIMINGS.computeIfAbsent(bucketName.get(), ignored -> new BucketTiming()).recordClassStart(startNanos);
        ACTIVE_CLASSES.put(testIdentifier.getUniqueId(), new ClassStart(bucketName.get(), testClass.get().getSimpleName(), startNanos));
    }

    static void recordExecutionFinished(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            recordTest(testIdentifier);
            return;
        }

        ClassStart classStart = ACTIVE_CLASSES.remove(testIdentifier.getUniqueId());
        if (classStart == null) {
            return;
        }

        BUCKET_TIMINGS.computeIfAbsent(classStart.bucketName(), ignored -> new BucketTiming()).recordClassFinish(classStart.className(), classStart.startNanos(),
                System.nanoTime());
    }

    static void recordExecutionSkipped(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            recordTest(testIdentifier);
        }
    }

    static void printSummary() {
        List<BucketTimingSnapshot> snapshots = BUCKET_DEFINITIONS.stream().map(definition -> new BucketTimingSnapshot(definition.name(), BUCKET_TIMINGS.get(definition.name())))
                .filter(BucketTimingSnapshot::hasTiming).sorted(Comparator.comparingLong(BucketTimingSnapshot::wallNanos).reversed()).toList();

        StringBuilder summary = new StringBuilder();
        appendBucketSummary(summary, snapshots);
        CpuUsageMonitor.summaryAndStop().ifPresent(summary::append);

        if (!summary.isEmpty()) {
            ParallelConsoleAppender.printToConsole(summary.toString());
        }
    }

    private static void appendBucketSummary(StringBuilder summary, List<BucketTimingSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }

        summary.append('\n');
        summary.append("=== Test Bucket Timing Summary ===\n");
        summary.append(String.format(Locale.ROOT, "%-34s %7s %8s %12s %12s %10s  %s%n", "Bucket", "Classes", "Tests", "Wall time", "Class time", "Gap", "Slowest class"));

        for (BucketTimingSnapshot snapshot : snapshots) {
            summary.append(String.format(Locale.ROOT, "%-34s %7d %8d %12s %12s %10s  %s%n", snapshot.bucketName(), snapshot.classes(), snapshot.tests(),
                    formatDuration(snapshot.wallNanos()), formatDuration(snapshot.classNanos()), formatDuration(snapshot.gapNanos()), snapshot.slowestClassDescription()));
        }

        summary.append('\n');
        summary.append("Slowest classes per bucket:\n");
        for (BucketTimingSnapshot snapshot : snapshots) {
            summary.append(snapshot.bucketName()).append(":\n");
            for (ClassTiming classTiming : snapshot.slowestClasses()) {
                summary.append(String.format(Locale.ROOT, "  %12s  %s%n", formatDuration(classTiming.durationNanos()), classTiming.className()));
            }
        }
    }

    private static boolean isClassContainer(TestIdentifier testIdentifier) {
        return testIdentifier.isContainer() && testIdentifier.getSource().filter(ClassSource.class::isInstance).isPresent();
    }

    private static void recordTest(TestIdentifier testIdentifier) {
        if (!COUNTED_TESTS.add(testIdentifier.getUniqueId())) {
            return;
        }

        Optional<String> bucketName = findBucketName(testIdentifier, getJavaClass(testIdentifier));
        bucketName.ifPresent(bucket -> BUCKET_TIMINGS.computeIfAbsent(bucket, ignored -> new BucketTiming()).recordTest());
    }

    private static Optional<String> findBucketName(TestIdentifier testIdentifier, Optional<Class<?>> testClass) {
        Optional<String> bucketFromTags = testIdentifier.getTags().stream().map(TestTag::getName).filter(BUCKET_NAMES::contains).findFirst();
        if (bucketFromTags.isPresent()) {
            return bucketFromTags;
        }

        return testClass.flatMap(TestBucketTiming::findBucketName);
    }

    private static Optional<String> findBucketName(Class<?> testClass) {
        for (BucketDefinition bucketDefinition : BUCKET_DEFINITIONS) {
            if (bucketDefinition.matches(testClass)) {
                return Optional.of(bucketDefinition.name());
            }
        }
        return Optional.empty();
    }

    private static Optional<Class<?>> getJavaClass(TestIdentifier testIdentifier) {
        return testIdentifier.getSource().flatMap(TestBucketTiming::getJavaClass);
    }

    private static Optional<Class<?>> getJavaClass(TestSource testSource) {
        if (testSource instanceof ClassSource classSource) {
            return Optional.of(classSource.getJavaClass());
        }
        if (testSource instanceof MethodSource methodSource) {
            return Optional.of(methodSource.getJavaClass());
        }
        return Optional.empty();
    }

    private static String formatDuration(long durationNanos) {
        long totalMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        long minutes = totalMillis / 60_000;
        long seconds = totalMillis % 60_000 / 1_000;
        long millis = totalMillis % 1_000;

        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %02d.%03ds", minutes, seconds, millis);
        }
        return String.format(Locale.ROOT, "%d.%03ds", seconds, millis);
    }

    private record BucketDefinition(String name, List<Class<?>> bucketClasses) {

        private boolean matches(Class<?> testClass) {
            return bucketClasses.stream().anyMatch(bucketClass -> bucketClass.isAssignableFrom(testClass));
        }
    }

    private record ClassStart(String bucketName, String className, long startNanos) {
    }

    private record ClassTiming(String className, long durationNanos) {
    }

    private static final class BucketTiming {

        private long wallStartNanos = Long.MAX_VALUE;

        private long wallEndNanos;

        private long classNanos;

        private int classes;

        private int tests;

        private ClassTiming slowestClass;

        private final List<ClassTiming> classTimings = new ArrayList<>();

        private synchronized void recordClassStart(long startNanos) {
            wallStartNanos = Math.min(wallStartNanos, startNanos);
        }

        private synchronized void recordClassFinish(String className, long startNanos, long endNanos) {
            long durationNanos = Math.max(0, endNanos - startNanos);
            ClassTiming classTiming = new ClassTiming(className, durationNanos);

            classes++;
            classNanos += durationNanos;
            wallStartNanos = Math.min(wallStartNanos, startNanos);
            wallEndNanos = Math.max(wallEndNanos, endNanos);
            classTimings.add(classTiming);

            if (slowestClass == null || durationNanos > slowestClass.durationNanos()) {
                slowestClass = classTiming;
            }
        }

        private synchronized void recordTest() {
            tests++;
        }

        private synchronized BucketTimingValues snapshot() {
            List<ClassTiming> slowestClasses = classTimings.stream().sorted(Comparator.comparingLong(ClassTiming::durationNanos).reversed()).limit(SLOW_CLASS_COUNT).toList();
            return new BucketTimingValues(classes, tests, wallNanos(), classNanos, slowestClass, slowestClasses);
        }

        private long wallNanos() {
            if (wallStartNanos == Long.MAX_VALUE || wallEndNanos == 0) {
                return 0;
            }
            return Math.max(0, wallEndNanos - wallStartNanos);
        }
    }

    private record BucketTimingValues(int classes, int tests, long wallNanos, long classNanos, ClassTiming slowestClass, List<ClassTiming> slowestClasses) {
    }

    private record BucketTimingSnapshot(String bucketName, BucketTimingValues values) {

        private BucketTimingSnapshot(String bucketName, BucketTiming bucketTiming) {
            this(bucketName, bucketTiming == null ? null : bucketTiming.snapshot());
        }

        private boolean hasTiming() {
            return values != null && (values.classes() > 0 || values.tests() > 0);
        }

        private int classes() {
            return values.classes();
        }

        private int tests() {
            return values.tests();
        }

        private long wallNanos() {
            return values.wallNanos();
        }

        private long classNanos() {
            return values.classNanos();
        }

        private long gapNanos() {
            return Math.max(0, wallNanos() - classNanos());
        }

        private String slowestClassDescription() {
            if (values.slowestClass() == null) {
                return "-";
            }
            return values.slowestClass().className() + " (" + formatDuration(values.slowestClass().durationNanos()) + ")";
        }

        private List<ClassTiming> slowestClasses() {
            return values.slowestClasses();
        }
    }
}
