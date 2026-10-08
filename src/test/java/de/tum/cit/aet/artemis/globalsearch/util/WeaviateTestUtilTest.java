package de.tum.cit.aet.artemis.globalsearch.util;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.awaitIndexing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
    void awaitIndexing_nestedCheckedExceptionSurfacesAsAssertionError() {
        assertThatThrownBy(() -> awaitIndexing(OUTER_CEILING, () -> awaitIndexing(() -> {
            throw new Exception("checked failure");
        }))).isInstanceOf(AssertionError.class).hasMessageContaining("checked failure");
    }

    @Test
    @Timeout(30)
    void awaitIndexing_aSecondTopLevelWaitStartsFreshAfterANestedOne() {
        awaitIndexing(OUTER_CEILING, () -> awaitIndexing(() -> assertThat(true).isTrue()));
        AtomicInteger attempts = new AtomicInteger();

        // If the nested marker leaked, this top-level call would run its assertion once and not retry it.
        awaitIndexing(OUTER_CEILING, () -> assertThat(attempts.incrementAndGet()).isGreaterThanOrEqualTo(2));

        assertThat(attempts).hasValueGreaterThanOrEqualTo(2);
    }

    @Test
    @Timeout(30)
    void awaitIndexing_topLevelTimeoutReportsTheLastFailureAndTheDispatcherState() {
        assertThatThrownBy(() -> awaitIndexing(Duration.ofSeconds(1), () -> {
            throw new AssertionError("never indexed");
        })).isInstanceOf(AssertionError.class).hasMessageContaining("never indexed").hasMessageContaining("Outbox dispatcher state at timeout");
    }
}
