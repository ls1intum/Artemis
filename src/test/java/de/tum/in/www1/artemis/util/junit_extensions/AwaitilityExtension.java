package de.tum.in.www1.artemis.util.junit_extensions;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A JUnit 5 extension that configures {@link Awaitility} to use a different default poll delay and interval.
 *
 * By default, {@link Awaitility} would use a poll delay and interval of 100ms, which makes tests run slower.
 */
public class AwaitilityExtension implements BeforeAllCallback {

    private static final Duration DEFAULT_POLL_DELAY = Duration.ZERO;

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(10);

    private static boolean configured;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!configured) {
            configured = true;
            Awaitility.setDefaultPollDelay(DEFAULT_POLL_DELAY);
            Awaitility.setDefaultPollInterval(DEFAULT_POLL_INTERVAL);
        }
    }
}
