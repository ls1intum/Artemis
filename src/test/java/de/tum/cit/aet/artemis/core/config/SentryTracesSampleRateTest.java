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
