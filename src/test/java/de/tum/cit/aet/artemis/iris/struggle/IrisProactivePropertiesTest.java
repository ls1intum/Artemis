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
}
