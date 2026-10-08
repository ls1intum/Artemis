package de.tum.cit.aet.artemis.globalsearch.util;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.BOUNDED_BACKOFF_POLL_INTERVAL;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.INDEXING_TIMEOUT;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.MAX_POLL_INTERVAL;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.OUTBOX_BASE_BACKOFF;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.OUTBOX_DRAIN_TICK;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.WEAVIATE_REST_TIMEOUT;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.awaitIndexing;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.describeDispatcherThreads;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.isInsideIndexingWait;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.runMarked;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.weaviateClientDefaultRestTimeout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.scheduling.annotation.Scheduled;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateOutboxProperties;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateOutboxDispatcher;

/**
 * Unit tests for the wait helper {@link WeaviateTestUtil#awaitIndexing}. No Spring context and no Weaviate are needed,
 * because the helper only wraps Awaitility.
 */
class WeaviateTestUtilTest {

    private static final Duration OUTER_CEILING = Duration.ofSeconds(3);

    @Test
    @Timeout(30)
    void awaitIndexing_retriesUntilTheAssertionHolds() {
        AtomicInteger attempts = new AtomicInteger();

        awaitIndexing(OUTER_CEILING, () -> assertThat(attempts.incrementAndGet()).isGreaterThanOrEqualTo(3));

        assertThat(attempts).hasValueGreaterThanOrEqualTo(3);
    }

    @Test
    @Timeout(30)
    void awaitIndexing_returnsAfterOneCheckWhenTheAssertionAlreadyHolds() {
        AtomicInteger attempts = new AtomicInteger();

        awaitIndexing(OUTER_CEILING, attempts::incrementAndGet);

        assertThat(attempts).as("a passing assertion must not be polled again").hasValue(1);
    }

    @Test
    @Timeout(30)
    void awaitIndexing_nestedCallRunsItsAssertionDirectlyInsteadOfWaitingAgain() {
        List<Duration> nestedDurations = new ArrayList<>();

        // The nested call uses the production ceiling of INDEXING_TIMEOUT (150 s). If it started a wait of its own, the
        // first nested call would block until the outer ceiling interrupts it instead of failing right away.
        assertThatThrownBy(() -> awaitIndexing(OUTER_CEILING, () -> {
            long start = System.nanoTime();
            try {
                awaitIndexing(() -> {
                    throw new AssertionError("nested check failed");
                });
            }
            finally {
                nestedDurations.add(Duration.ofNanos(System.nanoTime() - start));
            }
        })).isInstanceOf(AssertionError.class).hasMessageContaining("nested check failed");

        assertThat(nestedDurations).as("the outer wait polls several times, each nested failure is immediate").hasSizeGreaterThan(1);
        assertThat(nestedDurations).allSatisfy(duration -> assertThat(duration).isLessThan(Duration.ofSeconds(1)));
    }

    @Test
    @Timeout(30)
    void awaitIndexing_nestedCallSharesTheOuterDeadlineAndSucceedsWithIt() {
        AtomicInteger attempts = new AtomicInteger();

        awaitIndexing(OUTER_CEILING, () -> awaitIndexing(() -> assertThat(attempts.incrementAndGet()).isGreaterThanOrEqualTo(3)));

        assertThat(attempts).hasValueGreaterThanOrEqualTo(3);
    }

    @Test
    @Timeout(30)
    void awaitIndexing_nestedWaitInsideANestedWaitStillRunsDirectly() {
        AtomicInteger attempts = new AtomicInteger();

        awaitIndexing(OUTER_CEILING, () -> awaitIndexing(() -> awaitIndexing(() -> assertThat(attempts.incrementAndGet()).isGreaterThanOrEqualTo(2))));

        assertThat(attempts).hasValueGreaterThanOrEqualTo(2);
    }

    @Test
    @Timeout(30)
    void awaitIndexing_nestedCheckedExceptionBecomesAnAssertionErrorThatTheOuterWaitRetries() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> awaitIndexing(Duration.ofSeconds(1), () -> awaitIndexing(() -> {
            attempts.incrementAndGet();
            throw new Exception("checked failure");
        }))).isInstanceOf(AssertionError.class).hasMessageContaining("checked failure");

        assertThat(attempts).as("the nested failure is an assertion error, so the outer wait retries it until its own deadline").hasValueGreaterThan(1);
    }

    @Test
    @Timeout(30)
    void awaitIndexing_nestedRuntimeExceptionIsRethrownUnchangedAndNotRetried() {
        IllegalStateException failure = new IllegalStateException("nested runtime failure");
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> awaitIndexing(OUTER_CEILING, () -> awaitIndexing(() -> {
            attempts.incrementAndGet();
            throw failure;
        }))).isSameAs(failure);

        assertThat(attempts).as("a broken check is not an assertion failure and must not be retried").hasValue(1);
    }

    @Test
    @Timeout(30)
    void runMarked_marksTheThreadOnlyWhileTheAssertionRunsAndClearsItAfterAPassingAssertion() throws Throwable {
        AtomicBoolean markedInside = new AtomicBoolean();

        assertThat(isInsideIndexingWait()).isFalse();
        runMarked(() -> markedInside.set(isInsideIndexingWait()));

        assertThat(markedInside).as("the assertion runs with the marker set").isTrue();
        assertThat(isInsideIndexingWait()).as("a leaked marker would turn the next top-level wait on this thread into a single unretried check").isFalse();
    }

    @Test
    @Timeout(30)
    void runMarked_clearsTheMarkerAfterAFailingAssertion() {
        assertThatThrownBy(() -> runMarked(() -> {
            throw new AssertionError("failed while marked");
        })).isInstanceOf(AssertionError.class).hasMessage("failed while marked");

        assertThat(isInsideIndexingWait()).as("the marker must also be removed when the assertion throws").isFalse();
    }

    @Test
    @Timeout(30)
    void awaitIndexing_topLevelWaitNeverMarksTheCallingThread() {
        awaitIndexing(OUTER_CEILING, () -> assertThat(isInsideIndexingWait()).as("the assertion runs on the poll thread, marked").isTrue());

        assertThat(isInsideIndexingWait()).isFalse();
    }

    @Test
    @Timeout(30)
    void awaitIndexing_usesTheIndexingTimeoutAndTheBoundedBackoffPollInterval() {
        ConditionFactory factory = mock(ConditionFactory.class, RETURNS_SELF);
        try (MockedStatic<Awaitility> awaitility = mockStatic(Awaitility.class)) {
            awaitility.when(Awaitility::await).thenReturn(factory);

            awaitIndexing(() -> {
            });

            verify(factory).atMost(INDEXING_TIMEOUT);
            verify(factory).pollInterval(BOUNDED_BACKOFF_POLL_INTERVAL);
            verify(factory).untilAsserted(any(ThrowingRunnable.class));
        }
    }

    @Test
    @Timeout(30)
    void awaitIndexing_topLevelTimeoutReportsTheLastFailureAndTheDispatcherState() {
        assertThatThrownBy(() -> awaitIndexing(Duration.ofSeconds(1), () -> {
            throw new AssertionError("never indexed");
        })).isInstanceOf(AssertionError.class).hasMessageContaining("never indexed").hasMessageContaining("Outbox dispatcher state at timeout");
    }

    @Test
    @Timeout(30)
    void awaitIndexing_timeoutKeepsTheAwaitilityTimeoutAsCauseAndRetriesWithinTheCeiling() {
        AtomicInteger attempts = new AtomicInteger();
        long start = System.nanoTime();

        assertThatThrownBy(() -> awaitIndexing(Duration.ofSeconds(1), () -> {
            attempts.incrementAndGet();
            throw new AssertionError("never indexed");
        })).isInstanceOf(AssertionError.class).hasCauseInstanceOf(ConditionTimeoutException.class).hasMessageContaining("within 1 seconds");

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(attempts).as("the failing assertion is retried before the wait gives up").hasValueGreaterThan(2);
        assertThat(elapsed).as("the wait honours the ceiling it was given").isGreaterThanOrEqualTo(Duration.ofMillis(900)).isLessThan(Duration.ofSeconds(15));
    }

    @Test
    @Timeout(30)
    void awaitIndexing_topLevelNonAssertionExceptionFailsImmediatelyWithoutRetry() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> awaitIndexing(OUTER_CEILING, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("broken fixture");
        })).isInstanceOf(IllegalStateException.class).hasMessage("broken fixture");

        assertThat(attempts).as("only a failed assertion is worth waiting for, a broken check is not retried").hasValue(1);
    }

    @Test
    @Timeout(30)
    void awaitIndexing_timeoutDescribesABusyDispatcherWithItsThreadAndStack() throws Exception {
        CountDownLatch inDispatcher = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread writer = new Thread(() -> FakeWeaviateOutboxDispatcher.drain(inDispatcher, release), "fake-outbox-writer");
        writer.start();
        try {
            assertThat(inDispatcher.await(10, TimeUnit.SECONDS)).as("the fake dispatcher thread started").isTrue();

            assertThatThrownBy(() -> awaitIndexing(Duration.ofSeconds(1), () -> {
                throw new AssertionError("row not applied");
            })).isInstanceOf(AssertionError.class).hasMessageContaining("row not applied").hasMessageContaining("Outbox dispatcher state at timeout: busy")
                    .hasMessageContaining("Thread \"fake-outbox-writer\"").hasMessageContaining("FakeWeaviateOutboxDispatcher.drain").hasMessageNotContaining("so it is idle");
        }
        finally {
            release.countDown();
            writer.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    @Test
    @Timeout(30)
    void describeDispatcherThreads_limitsTheReportedStackToFifteenFrames() throws Exception {
        CountDownLatch inDispatcher = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread writer = new Thread(() -> FakeDeepStackWriter.drainRecursively(30, inDispatcher, release), "fake-deep-writer");
        writer.start();
        try {
            assertThat(inDispatcher.await(10, TimeUnit.SECONDS)).isTrue();

            String description = describeDispatcherThreads("FakeDeepStackWriter");

            assertThat(description).contains("fake-deep-writer");
            assertThat(description.lines().filter(line -> line.startsWith("    at "))).hasSize(15);
        }
        finally {
            release.countDown();
            writer.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    @Test
    @Timeout(30)
    void describeDispatcherThreads_reportsAnIdleDispatcherWhenNoThreadRunsIt() {
        String description = describeDispatcherThreads("NoSuchDispatcherClass4711");

        assertThat(description).startsWith("Outbox dispatcher state at timeout: no thread is executing it, so it is idle").contains("deferred by retry backoff")
                .doesNotContain("busy, a slow Weaviate write");
    }

    @Test
    void indexingTimeoutCoversTheWorstCaseRecoveryOfTheDispatcher() {
        assertThat(WEAVIATE_REST_TIMEOUT).as("test copy of the Weaviate client default query timeout, which the production client does not override")
                .isEqualTo(weaviateClientDefaultRestTimeout());
        // two hung REST calls (one ahead in the queue, one of the awaited row), the retry backoff and one drain tick, plus a margin
        assertThat(INDEXING_TIMEOUT).isEqualTo(Duration.ofSeconds(150));
        assertThat(INDEXING_TIMEOUT).isGreaterThan(WEAVIATE_REST_TIMEOUT.multipliedBy(2).plus(OUTBOX_BASE_BACKOFF).plus(OUTBOX_DRAIN_TICK));
    }

    @Test
    void indexingTimeoutSizingMatchesTheProductionOutboxDefaults() throws Exception {
        WeaviateOutboxProperties defaults = new Binder(new MapConfigurationPropertySource()).bindOrCreate("artemis.weaviate.outbox", WeaviateOutboxProperties.class);
        Scheduled scheduled = WeaviateOutboxDispatcher.class.getMethod("scheduledDrain").getAnnotation(Scheduled.class);
        Matcher placeholderDefault = Pattern.compile("^\\$\\{artemis\\.weaviate\\.outbox\\.drain-interval-seconds:(\\d+)}$").matcher(scheduled.fixedDelayString());

        assertThat(OUTBOX_BASE_BACKOFF).as("test copy of WeaviateOutboxProperties#baseBackoffSeconds").isEqualTo(Duration.ofSeconds(defaults.baseBackoffSeconds()));
        assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.SECONDS);
        assertThat(placeholderDefault.matches()).as("the @Scheduled placeholder of the dispatcher drain tick: " + scheduled.fixedDelayString()).isTrue();
        // the record default is not what the scheduler applies: the placeholder default is
        assertThat(OUTBOX_DRAIN_TICK).as("test copy of the default in the @Scheduled placeholder of WeaviateOutboxDispatcher#scheduledDrain")
                .isEqualTo(Duration.ofSeconds(Long.parseLong(placeholderDefault.group(1))));
        assertThat(OUTBOX_DRAIN_TICK).as("the record default declared for strict binding must agree with the placeholder")
                .isEqualTo(Duration.ofSeconds(defaults.drainIntervalSeconds()));
    }

    @Test
    void pollIntervalStartsFastDoublesAndIsCapped() {
        List<Duration> intervals = new ArrayList<>();
        Duration previous = Duration.ZERO;
        for (int pollCount = 1; pollCount <= 8; pollCount++) {
            previous = BOUNDED_BACKOFF_POLL_INTERVAL.next(pollCount, previous);
            intervals.add(previous);
        }

        assertThat(intervals).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(400), Duration.ofMillis(800), Duration.ofMillis(1600),
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(MAX_POLL_INTERVAL).isEqualTo(Duration.ofSeconds(2));
    }

    /**
     * Stands in for the outbox dispatcher: the description of the dispatcher finds it by the class name on a thread's
     * stack, so a class of this name that is blocked inside one of its methods looks like a dispatcher in a slow write.
     */
    private static final class FakeWeaviateOutboxDispatcher {

        static void drain(CountDownLatch entered, CountDownLatch release) {
            entered.countDown();
            awaitRelease(release);
        }
    }

    /**
     * Blocks below a deep stack. It has a name of its own, so that the tests of the stack limit and of the busy
     * dispatcher, which JUnit may run at the same time, never see each other's thread.
     */
    private static final class FakeDeepStackWriter {

        static void drainRecursively(int depth, CountDownLatch entered, CountDownLatch release) {
            if (depth == 0) {
                entered.countDown();
                awaitRelease(release);
                return;
            }
            drainRecursively(depth - 1, entered, release);
        }
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            release.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
