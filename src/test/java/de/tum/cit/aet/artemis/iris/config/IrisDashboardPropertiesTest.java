package de.tum.cit.aet.artemis.iris.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class IrisDashboardPropertiesTest {

    @Test
    void rejectsInvalidTopLevelDurations() {
        IrisDashboardProperties properties = new IrisDashboardProperties();

        assertThatThrownBy(() -> properties.setMaxQueryWindowDays(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxQueryWindowDays");
        assertThatThrownBy(() -> properties.setStaleThresholdMinutes(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("staleThresholdMinutes");
    }

    @Test
    void validatesDigestConfiguration() {
        IrisDashboardProperties.Digest digest = new IrisDashboardProperties.Digest();

        assertThatThrownBy(() -> digest.setCron(" ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cron");
        assertThatThrownBy(() -> digest.setCron("not a cron")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> digest.setRecipients(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("recipients");

        digest.setCron("0 0 7 * * *");
        digest.setRecipients(List.of("admin@example.com"));

        assertThat(digest.getRecipients()).containsExactly("admin@example.com");
    }

    @Test
    void validatesAlertConfiguration() {
        IrisDashboardProperties.Alert alert = new IrisDashboardProperties.Alert();

        assertThatThrownBy(() -> alert.setNoResponseRateThreshold(-1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("noResponseRateThreshold");
        assertThatThrownBy(() -> alert.setNoResponseRateThreshold(101)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("noResponseRateThreshold");
        assertThatThrownBy(() -> alert.setCheckIntervalMinutes(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("checkIntervalMinutes");
        assertThatThrownBy(() -> alert.setCooldownMinutes(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cooldownMinutes");
        assertThatThrownBy(() -> alert.setLookbackMinutes(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lookbackMinutes");
        assertThatThrownBy(() -> alert.setMinimumActiveSessions(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minimumActiveSessions");
        assertThatThrownBy(() -> alert.setMinimumUserMessages(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minimumUserMessages");
        assertThatThrownBy(() -> alert.setRecipients(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("recipients");

        alert.setNoResponseRateThreshold(100);
        alert.setRecipients(List.of("admin@example.com"));

        assertThat(alert.getRecipients()).containsExactly("admin@example.com");
    }
}
