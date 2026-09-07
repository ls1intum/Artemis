package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A JUnit 5 extension that configures {@link Awaitility} to use a different default poll delay, interval and timeout.
 * <p>
 * By default, {@link Awaitility} would use a poll delay and interval of 100ms, which makes tests run slower.
 */
public class AwaitilityExtension implements BeforeAllCallback {

    private static final Duration DEFAULT_POLL_DELAY = Duration.ZERO;

    /**
     * Default timeout for a bare {@code await()}, raised from Awaitility's own 10 seconds.
     * <p>
     * Over a hundred call sites use {@code await()} without an explicit {@code atMost(...)} and so took the library
     * default, while the calls that do state a timeout overwhelmingly use 30 seconds for the same kind of wait - an
     * asynchronous scheduler or a websocket message settling after a database-heavy setup. Ten seconds is enough on an
     * idle machine and not enough on a loaded CI runner, which showed up as whole classes failing at once with
     * {@code ConditionTimeoutException ... was not fulfilled within 10 seconds} while passing locally.
     * <p>
     * Raising it cannot turn a passing test into a failing one: it only lets a condition that would have been met take
     * longer to be observed. The cost is that a genuinely stuck async path takes 30 seconds to report instead of 10.
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration POLL_INTERVAL_START = Duration.ofMillis(100);

    private static final long POLL_INTERVAL_MULTIPLIER = 2;

    private static boolean configured;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!configured) {
            configured = true;
            Awaitility.setDefaultTimeout(DEFAULT_TIMEOUT);
            Awaitility.setDefaultPollDelay(DEFAULT_POLL_DELAY);
            Awaitility.setDefaultPollInterval((pollCount, previousDuration) -> pollCount == 1 ? POLL_INTERVAL_START : previousDuration.multipliedBy(POLL_INTERVAL_MULTIPLIER));
        }
    }
}
