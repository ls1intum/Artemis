package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;

class IrisProactivePropertiesTest {

    private static IrisProactiveProperties withJobTimeout(int seconds) {
        var properties = new IrisProactiveProperties();
        ReflectionTestUtils.setField(properties, "jobTimeoutSeconds", seconds);
        return properties;
    }

    @Test
    void defaultsMatchTheValuesTheCodeUsedBeforeTheyWereConfigurable() {
        var properties = new IrisProactiveProperties();

        assertThat(properties.getAbandonedEpisodeRetention()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.getEngagedReplyWindow()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getPersistMaxAttempts()).isEqualTo(3);
        assertThat(properties.getCleanupCron()).isEqualTo("0 30 3 * * *");
    }

    @Test
    void theTwoPropertiesThatUsedToBeBareValueAnnotationsKeepTheirKeysAndDefaults() {
        var properties = new IrisProactiveProperties();

        assertThat(properties.isLegacyBuildTriggers()).isTrue();
        assertThat(properties.getStruggle().getConfidenceThreshold()).isEqualTo(0.6);
    }

    @Test
    void aConfidenceThresholdOutsideTheUnitIntervalIsRejected() {
        var properties = withJobTimeout(300);
        properties.getStruggle().setConfidenceThreshold(1.5);

        assertThatIllegalArgumentException().isThrownBy(properties::validate).withMessageContaining("confidence-threshold");
    }

    @Test
    void aNaNConfidenceThresholdIsRejected() {
        var properties = withJobTimeout(300);
        // Reaches the binder as YAML `.NaN` or as the string `NaN`. It passes a plain range check, and afterwards
        // every confidence compares as not below it, so the gate the threshold exists for stands open.
        properties.getStruggle().setConfidenceThreshold(Double.NaN);

        assertThatIllegalArgumentException().isThrownBy(properties::validate).withMessageContaining("confidence-threshold");
    }

    @Test
    void theDefaultRetentionPassesValidationAgainstTheDefaultJobTimeout() {
        assertThatCode(() -> withJobTimeout(300).validate()).doesNotThrowAnyException();
    }

    @Test
    void aRetentionThatCouldReapAnEpisodeAPyrisCallbackStillNeedsIsRejected() {
        var properties = withJobTimeout(300);
        properties.setAbandonedEpisodeRetention(Duration.ofMinutes(20));

        assertThatIllegalArgumentException().isThrownBy(properties::validate).withMessageContaining("artemis.iris.jobs.timeout");
    }

    @Test
    void aJobTimeoutRaisedPastTheRetentionIsRejectedToo() {
        // The invariant is a ratio, not a fixed number, so it also has to catch the other side being moved.
        var properties = withJobTimeout(300_000);

        assertThatIllegalArgumentException().isThrownBy(properties::validate).withMessageContaining("abandoned-episode-retention");
    }

    @Test
    void aNonPositiveJobTimeoutIsRejected() {
        // The ratio check would otherwise pass trivially against a zero or negative timeout, while PyrisJobService
        // uses that same value as the TTL of the job and in-flight maps.
        assertThatIllegalArgumentException().isThrownBy(() -> withJobTimeout(0).validate()).withMessageContaining("artemis.iris.jobs.timeout");
    }

    @Test
    void aNonPositiveEngagementWindowIsRejected() {
        var properties = withJobTimeout(300);
        properties.setEngagedReplyWindow(Duration.ZERO);

        assertThatIllegalArgumentException().isThrownBy(properties::validate).withMessageContaining("engaged-reply-window");
    }

    @Test
    void aRetryCountBelowOneIsRejected() {
        var properties = withJobTimeout(300);
        properties.setPersistMaxAttempts(0);

        assertThatIllegalArgumentException().isThrownBy(properties::validate).withMessageContaining("persist-max-attempts");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "retention", "replyWindow", "negativeReply", "missingCooldown", "negativeCooldown", "zeroCooldown",
            "negativeConfidence" })
    void invalidOperatorSettingsAreRejectedWithTheAffectedProperty(String invalid) {
        var properties = withJobTimeout(300);
        String expected;
        switch (invalid) {
            case "retention" -> {
                properties.setAbandonedEpisodeRetention(null);
                expected = "must be set";
            }
            case "replyWindow" -> {
                properties.setEngagedReplyWindow(null);
                expected = "must be set";
            }
            case "negativeReply" -> {
                properties.setEngagedReplyWindow(Duration.ofSeconds(-1));
                expected = "engaged-reply-window must be positive";
            }
            case "missingCooldown" -> {
                properties.setTriggerCooldown(null);
                expected = "trigger-cooldown must be positive";
            }
            case "negativeCooldown" -> {
                properties.setTriggerCooldown(Duration.ofSeconds(-1));
                expected = "trigger-cooldown must be positive";
            }
            case "zeroCooldown" -> {
                properties.setTriggerCooldown(Duration.ZERO);
                expected = "trigger-cooldown must be positive";
            }
            default -> {
                properties.getStruggle().setConfidenceThreshold(-0.01);
                expected = "confidence-threshold must be within [0, 1]";
            }
        }
        assertThatIllegalArgumentException().isThrownBy(properties::validate).withMessageContaining(expected);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(doubles = { 0.0, 1.0 })
    void confidenceEndpointsAndMinimumSafeRetentionAreAccepted(double confidence) {
        var properties = withJobTimeout(300);
        properties.setAbandonedEpisodeRetention(Duration.ofSeconds(3000));
        properties.setEngagedReplyWindow(Duration.ofNanos(1));
        properties.setTriggerCooldown(Duration.ofNanos(1));
        properties.getStruggle().setConfidenceThreshold(confidence);
        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.getStruggle().getConfidenceThreshold()).isEqualTo(confidence);
    }

}
