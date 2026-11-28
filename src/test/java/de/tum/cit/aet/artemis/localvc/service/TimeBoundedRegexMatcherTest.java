package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for matching a name against an expression within a time budget.
 */
class TimeBoundedRegexMatcherTest {

    @Test
    void aMatchingNameIsAccepted() {
        assertThat(TimeBoundedRegexMatcher.matches(Pattern.compile("feature/.*"), "feature/login")).isTrue();
    }

    @Test
    void aNameThatDoesNotMatchIsRefused() {
        assertThat(TimeBoundedRegexMatcher.matches(Pattern.compile("feature/.*"), "hotfix")).isFalse();
    }

    @Test
    void aMatchThatNeedsTooMuchTimeEndsAndCountsAsNoMatch() {
        // repeated groups with a wildcard backtrack very deeply on a name that almost matches
        Pattern pattern = Pattern.compile("(.*a){12}");
        long start = System.nanoTime();

        boolean matches = TimeBoundedRegexMatcher.matches(pattern, "a".repeat(30) + "b", TimeUnit.MILLISECONDS.toNanos(100));

        assertThat(matches).isFalse();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).as("the match ends at the time budget").isGreaterThanOrEqualTo(100).isLessThan(2000);
    }
}
