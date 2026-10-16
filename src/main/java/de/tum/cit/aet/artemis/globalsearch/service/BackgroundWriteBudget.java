package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.Clock;

/**
 * Per-minute budget for background index work: row writes and embedding requests, each a token bucket that refills
 * continuously up to one minute's worth.
 * <p>
 * Only the outbox dispatcher's single thread uses it, on the single scheduling node, so it needs neither
 * synchronization nor cross-node state. A caller asks before each operation and is charged even if the operation then
 * fails, so a failing endpoint cannot be hit faster than the budget allows. The dispatcher never waits for a budget: an
 * exhausted budget ends its turn, which keeps it free for writes caused by users.
 */
class BackgroundWriteBudget {

    private final TokenBucket rows;

    private final TokenBucket requests;

    BackgroundWriteBudget(int rowsPerMinute, int requestsPerMinute, Clock clock) {
        this.rows = new TokenBucket(rowsPerMinute, clock);
        this.requests = new TokenBucket(requestsPerMinute, clock);
    }

    /**
     * Takes budget for one background row write.
     *
     * @return whether the write may happen now
     */
    boolean tryAcquireRow() {
        return rows.tryAcquire();
    }

    /**
     * Takes budget for one background embedding request.
     *
     * @return whether the request may be sent now
     */
    boolean tryAcquireRequest() {
        return requests.tryAcquire();
    }

    /**
     * Whether at least one row write is available, without taking it.
     *
     * @return whether the next background step can start
     */
    boolean hasRowCapacity() {
        return rows.available() >= 1;
    }

    private static final class TokenBucket {

        private static final double MILLIS_PER_MINUTE = 60_000d;

        private final int capacity;

        private final Clock clock;

        private double tokens;

        private long lastRefillMillis;

        private TokenBucket(int perMinute, Clock clock) {
            this.capacity = perMinute;
            this.clock = clock;
            this.tokens = perMinute;
            this.lastRefillMillis = clock.millis();
        }

        private boolean tryAcquire() {
            refill();
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }

        private double available() {
            refill();
            return tokens;
        }

        private void refill() {
            long now = clock.millis();
            if (now > lastRefillMillis) {
                tokens = Math.min(capacity, tokens + (now - lastRefillMillis) * capacity / MILLIS_PER_MINUTE);
                lastRefillMillis = now;
            }
        }
    }
}
