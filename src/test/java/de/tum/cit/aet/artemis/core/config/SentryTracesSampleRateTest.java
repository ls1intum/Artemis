package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The traces sample rate has to come from configuration when a deployment sets one.
 * <p>
 * It did not: {@code sentry.traces-sample-rate} was rendered into every deployment's configuration and read by
 * nobody, because the rate was derived from the environment name alone. Every environment whose name contains
 * "staging" or "test" therefore traced 100% of requests no matter what its configuration said, and production traced
 * 5% rather than the 0.2 its inventory asked for.
 */
class SentryTracesSampleRateTest {

    private double rateFor(String environment, Double configured) {
        SentryConfiguration configuration = new SentryConfiguration();
        ReflectionTestUtils.setField(configuration, "environment", Optional.of(environment));
        ReflectionTestUtils.setField(configuration, "isTestServer", Optional.empty());
        ReflectionTestUtils.setField(configuration, "configuredTracesSampleRate", Optional.ofNullable(configured));
        return (double) ReflectionTestUtils.invokeMethod(configuration, "getTracesSampleRate");
    }

    private String scrub(String message) {
        return ReflectionTestUtils.invokeMethod(new SentryConfiguration(), "scrubStringMessage", message);
    }

    @Test
    @DisplayName("The user data a message carries is scrubbed")
    void personalDataIsScrubbed() {
        // None of this worked: the expression for the User{...} form was invalid ("User{" reads as the start of a
        // repetition), so every call threw PatternSyntaxException rather than scrubbing anything. It only surfaced
        // once the expressions were compiled up front instead of on every call.
        assertThat(scrub("failed for user=barney_young while saving")).doesNotContain("barney_young");
        assertThat(scrub("rejected User{login=barney_young, id=42} twice")).doesNotContain("barney_young").doesNotContain("42");
        assertThat(scrub("mail to barney.young@tum.de bounced")).doesNotContain("barney.young@tum.de");
        assertThat(scrub("nothing personal here")).isEqualTo("nothing personal here");
    }

    @Test
    @DisplayName("A configured rate wins over the environment default")
    void configuredRateWins() {
        assertThat(rateFor("staging2", 0.05)).isEqualTo(0.05);
        assertThat(rateFor("prod", 0.2)).isEqualTo(0.2);
    }

    @Test
    @DisplayName("Turning tracing off entirely is possible from configuration")
    void configuredZeroIsHonoured() {
        // Zero must not be mistaken for "unset" and fall back to the environment default, which is what makes the
        // setting usable for a benchmark run that does not want to pay for tracing at all.
        assertThat(rateFor("staging2", 0.0)).isZero();
    }

    @Test
    @DisplayName("Without configuration the environment still decides")
    void environmentDefaultsAreUnchanged() {
        assertThat(rateFor("staging2", null)).isEqualTo(1.0);
        assertThat(rateFor("test5", null)).isEqualTo(1.0);
        assertThat(rateFor("prod", null)).isEqualTo(0.05);
        assertThat(rateFor("local", null)).isZero();
    }
}
