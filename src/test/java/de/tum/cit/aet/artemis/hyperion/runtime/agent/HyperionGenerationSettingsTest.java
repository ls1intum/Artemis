package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class HyperionGenerationSettingsTest {

    private final HyperionGenerationSettings settings = settings(10, Duration.ofMinutes(20), 1_000, 32_000);

    @Test
    void absentAndWiderRequestsKeepTheOriginalProfile() {
        assertThat(settings.tightenedBy(null, null)).isSameAs(settings);
        assertThat(settings.tightenedBy(2_000L, Duration.ofMinutes(30))).isSameAs(settings);
        assertThat(settings.tightenedBy(1_000L, Duration.ofMinutes(20))).isSameAs(settings);
    }

    @Test
    void tokenOnlyLimitKeepsEngineDefaultsAndOtherProfileValues() {
        assertThat(settings.tightenedBy(400L, null))
                .isEqualTo(new HyperionGenerationSettings("review", "Review", 10, Duration.ofMinutes(20), 400, true, "CONTINUOUS", 32_000, null, true, false));
        assertThat(settings.maxTokensPerJob()).isEqualTo(1_000);
    }

    @Test
    void shorterDeadlineDisablesSharedEngineDefaultsWithoutWideningTokens() {
        assertThat(settings.tightenedBy(2_000L, Duration.ofMinutes(5)))
                .isEqualTo(new HyperionGenerationSettings("review", "Review", 10, Duration.ofMinutes(5), 1_000, true, "CONTINUOUS", 32_000, null, false, false));
    }

    @Test
    void tighteningPreservesAnExistingEngineOverride() {
        var overridden = new HyperionGenerationSettings("custom", null, 10, Duration.ofMinutes(20), 1_000, false, "FRESH", 32_000, null, false, true);
        assertThat(overridden.tightenedBy(400L, null))
                .isEqualTo(new HyperionGenerationSettings("custom", null, 10, Duration.ofMinutes(20), 400, false, "FRESH", 32_000, null, false, true));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1 })
    void nonPositiveTurnTokenAndContextBoundsAreRejected(int bound) {
        assertThatIllegalArgumentException().isThrownBy(() -> settings(bound, Duration.ofMinutes(20), 1_000, 32_000)).withMessageContaining("max-turns");
        assertThatIllegalArgumentException().isThrownBy(() -> settings(10, Duration.ofMinutes(20), bound, 32_000)).withMessageContaining("max-tokens-per-job");
        assertThatIllegalArgumentException().isThrownBy(() -> settings(10, Duration.ofMinutes(20), 1_000, bound)).withMessageContaining("context-window-tokens");
        assertThatIllegalArgumentException().isThrownBy(() -> settings.tightenedBy((long) bound, null)).withMessageContaining("max-tokens-per-job");
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("invalidDurations")
    void missingAndNonPositiveDeadlinesAreRejected(Duration duration) {
        assertThatIllegalArgumentException().isThrownBy(() -> settings(10, duration, 1_000, 32_000)).withMessageContaining("max-job-duration");
        if (duration != null) {
            assertThatIllegalArgumentException().isThrownBy(() -> settings.tightenedBy(null, duration)).withMessageContaining("max-job-duration");
        }
    }

    private static Stream<Duration> invalidDurations() {
        return Stream.of(Duration.ZERO, Duration.ofSeconds(-1));
    }

    private static HyperionGenerationSettings settings(int turns, Duration duration, long tokens, int context) {
        return new HyperionGenerationSettings("review", "Review", turns, duration, tokens, true, "CONTINUOUS", context, null, true, false);
    }
}
