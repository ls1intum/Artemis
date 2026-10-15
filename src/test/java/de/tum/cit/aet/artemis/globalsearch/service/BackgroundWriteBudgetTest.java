package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class BackgroundWriteBudgetTest {

    /**
     * A clock the test moves by hand.
     */
    private static final class ManualClock extends Clock {

        private Instant now = Instant.parse("2026-10-12T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void rowsStartWithOneMinuteOfBudgetAndRefillContinuously() {
        var clock = new ManualClock();
        var budget = new BackgroundWriteBudget(60, 30, clock);

        for (int i = 0; i < 60; i++) {
            assertThat(budget.tryAcquireRow()).isTrue();
        }
        assertThat(budget.tryAcquireRow()).isFalse();
        assertThat(budget.hasRowCapacity()).isFalse();

        // 60 rows per minute: one row per second.
        clock.advance(Duration.ofMillis(999));
        assertThat(budget.hasRowCapacity()).isFalse();
        clock.advance(Duration.ofMillis(1));
        assertThat(budget.tryAcquireRow()).isTrue();
        assertThat(budget.tryAcquireRow()).isFalse();
    }

    @Test
    void refillNeverExceedsOneMinuteOfBudget() {
        var clock = new ManualClock();
        var budget = new BackgroundWriteBudget(2, 30, clock);

        clock.advance(Duration.ofHours(1));

        assertThat(budget.tryAcquireRow()).isTrue();
        assertThat(budget.tryAcquireRow()).isTrue();
        assertThat(budget.tryAcquireRow()).isFalse();
    }

    @Test
    void requestsHaveTheirOwnBudget() {
        var clock = new ManualClock();
        var budget = new BackgroundWriteBudget(600, 2, clock);

        assertThat(budget.tryAcquireRequest()).isTrue();
        assertThat(budget.tryAcquireRequest()).isTrue();
        assertThat(budget.tryAcquireRequest()).isFalse();
        assertThat(budget.tryAcquireRow()).as("rows are counted separately").isTrue();

        // 2 requests per minute: one every 30 seconds.
        clock.advance(Duration.ofSeconds(30));
        assertThat(budget.tryAcquireRequest()).isTrue();
        assertThat(budget.tryAcquireRequest()).isFalse();
    }
}
