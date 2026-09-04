package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Per-request budget bounds may only narrow a profile: nobody needs a role to lower their own ceiling, and no value can raise one.
 */
class HyperionGenerationSettingsTest {

    private static HyperionGenerationSettings profile() {
        return new HyperionGenerationSettings("standard", "Standard", 60, Duration.ofMinutes(45), 3_000_000L, true, "CONTINUOUS", 128_000, null, true, false);
    }

    @Test
    void requestBelowTheCeiling_narrowsBothBounds() {
        HyperionGenerationSettings tightened = profile().tightenedBy(600_000L, Duration.ofMinutes(12));

        assertThat(tightened.maxTokensPerJob()).isEqualTo(600_000L);
        assertThat(tightened.maxJobDuration()).isEqualTo(Duration.ofMinutes(12));
        assertThat(tightened.name()).isEqualTo("standard");
    }

    @Test
    void requestAboveTheCeiling_clampsInsteadOfRaisingOrRejecting() {
        HyperionGenerationSettings tightened = profile().tightenedBy(999_000_000L, Duration.ofHours(10));

        assertThat(tightened.maxTokensPerJob()).isEqualTo(3_000_000L);
        assertThat(tightened.maxJobDuration()).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void requestWithoutBounds_keepsTheProfileUnchanged() {
        HyperionGenerationSettings profile = profile();

        assertThat(profile.tightenedBy(null, null)).isSameAs(profile);
    }

    @Test
    void narrowingOnlyTheTokenBound_keepsTheEngineOnTheSharedSingletons() {
        // The token bound is enforced by the run's usage sink, which the shared engine objects already read per run.
        HyperionGenerationSettings tightened = profile().tightenedBy(600_000L, null);

        assertThat(tightened.engineDefaults()).isTrue();
    }

    @Test
    void narrowingTheWallClockBound_forcesTheRunOffTheSharedSingletons() {
        // The deadline sizes the staged authoring budget, so a run with a shorter one must not keep starting stages against the deployment-wide budget.
        HyperionGenerationSettings tightened = profile().tightenedBy(null, Duration.ofMinutes(12));

        assertThat(tightened.engineDefaults()).isFalse();
        assertThat(tightened.maxJobDuration()).isEqualTo(Duration.ofMinutes(12));
    }

    @Test
    void narrowingNeverWidensTheProfileIdentityOrItsProviderOptions() {
        HyperionGenerationSettings pinned = new HyperionGenerationSettings("thorough", "Thorough", 90, Duration.ofMinutes(60), 6_000_000L, false, "FRESH", 256_000, null, false,
                true);

        HyperionGenerationSettings tightened = pinned.tightenedBy(1L, Duration.ofSeconds(1));

        assertThat(tightened.name()).isEqualTo("thorough");
        assertThat(tightened.maxTurns()).isEqualTo(90);
        assertThat(tightened.stagedGeneration()).isFalse();
        assertThat(tightened.stagedContext()).isEqualTo("FRESH");
        assertThat(tightened.contextWindowTokens()).isEqualTo(256_000);
        assertThat(tightened.providerOptionsOverride()).isTrue();
    }
}
