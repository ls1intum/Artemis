package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a provider request again after a failure that provably produced no completion ({@link ProviderFailureClass#retryable()}).
 * <p>
 * The SDK's own retry policy is disabled where accounting must be exact, because an SDK retry cannot be attributed from the returned response. These retries are
 * attributable: each failed attempt is known to have produced no completion, so repeating it duplicates nothing. Five doubling waits from a ten-second base span roughly
 * five minutes, the length of provider outage worth waiting out inside a run.
 */
public final class ProviderRetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(ProviderRetryPolicy.class);

    static final int RETRIES = 5;

    private final long baseMillis;

    private final long capMillis;

    public ProviderRetryPolicy() {
        this(10_000L, 120_000L);
    }

    /** Explicit timing, so tests can retry without sleeping. */
    public ProviderRetryPolicy(long baseMillis, long capMillis) {
        this.baseMillis = baseMillis;
        this.capMillis = capMillis;
    }

    /**
     * Runs the request until it returns or a failure is not worth repeating: one that may already have consumed usage, one the same request cannot get past, the retry
     * budget, or cancellation during a wait. Rethrows the last failure.
     *
     * @param request       the provider request
     * @param cancelled     polled after each wait
     * @param retryListener told once per retry, in user-facing words; may be {@code null}
     * @param what          names the request in the log, e.g. {@code "turn 12"}
     * @param <T>           the response type
     * @return the response
     */
    @Nullable
    public <T> T execute(Supplier<T> request, BooleanSupplier cancelled, @Nullable Consumer<String> retryListener, String what) {
        for (int attempt = 1;; attempt++) {
            try {
                return request.get();
            }
            catch (RuntimeException e) {
                ProviderFailureClass failure = ProviderFailureClass.of(e);
                if (!failure.retryable() || attempt > RETRIES) {
                    throw e;
                }
                long backoff = Math.min(capMillis, baseMillis * (1L << (attempt - 1)));
                log.warn("Provider request for {} failed ({}, {}); retrying in {} ms ({} of {})", what, failure, ProviderFailureClass.describe(e), backoff, attempt, RETRIES);
                if (retryListener != null) {
                    retryListener.accept("The AI service is temporarily unavailable; retrying (" + attempt + " of " + RETRIES + ").");
                }
                if (!backOff(backoff, baseMillis, cancelled)) {
                    throw e;
                }
            }
        }
    }

    /**
     * Sleeps with jitter.
     *
     * @return {@code true} to continue, {@code false} if cancellation or interruption was observed
     */
    static boolean backOff(long backoffMillis, long jitterMillis, BooleanSupplier cancelled) {
        if (backoffMillis <= 0) {
            return !cancelled.getAsBoolean();
        }
        long backoff = backoffMillis + ThreadLocalRandom.current().nextLong(jitterMillis + 1);
        try {
            Thread.sleep(backoff);
            return !cancelled.getAsBoolean();
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while backing off before another provider request");
            return false;
        }
    }
}
