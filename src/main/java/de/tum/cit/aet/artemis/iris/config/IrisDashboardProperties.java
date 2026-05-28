package de.tum.cit.aet.artemis.iris.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

@Configuration
@Lazy(false)
@Profile(PROFILE_CORE)
@Conditional(IrisEnabled.class)
@ConfigurationProperties(prefix = "artemis.iris.dashboard")
public class IrisDashboardProperties {

    private int maxQueryWindowDays = 90;

    private int staleThresholdMinutes = 5;

    private final Digest digest = new Digest();

    private final Alert alert = new Alert();

    /**
     * Validates that the configured property values are within acceptable ranges.
     */
    @PostConstruct
    public void validate() {
        if (maxQueryWindowDays <= 0) {
            throw new IllegalArgumentException("artemis.iris.dashboard.max-query-window-days must be > 0");
        }
        if (staleThresholdMinutes <= 0) {
            throw new IllegalArgumentException("artemis.iris.dashboard.stale-threshold-minutes must be > 0");
        }
    }

    public int getMaxQueryWindowDays() {
        return maxQueryWindowDays;
    }

    public void setMaxQueryWindowDays(int maxQueryWindowDays) {
        this.maxQueryWindowDays = maxQueryWindowDays;
    }

    public int getStaleThresholdMinutes() {
        return staleThresholdMinutes;
    }

    public void setStaleThresholdMinutes(int staleThresholdMinutes) {
        this.staleThresholdMinutes = staleThresholdMinutes;
    }

    public Digest getDigest() {
        return digest;
    }

    public Alert getAlert() {
        return alert;
    }

    public static class Digest {

        private boolean enabled = false;

        private String cron = "0 0 7 * * *";

        private List<String> recipients = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(List<String> recipients) {
            this.recipients = recipients;
        }
    }

    public static class Alert {

        private boolean enabled = false;

        private double noResponseRateThreshold = 10;

        private int checkIntervalMinutes = 30;

        private int cooldownMinutes = 360;

        private int lookbackMinutes = 60;

        private int minimumEligibleSessions = 10;

        private int minimumUserMessages = 20;

        private List<String> recipients = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getNoResponseRateThreshold() {
            return noResponseRateThreshold;
        }

        public void setNoResponseRateThreshold(double noResponseRateThreshold) {
            this.noResponseRateThreshold = noResponseRateThreshold;
        }

        public int getCheckIntervalMinutes() {
            return checkIntervalMinutes;
        }

        public void setCheckIntervalMinutes(int checkIntervalMinutes) {
            this.checkIntervalMinutes = checkIntervalMinutes;
        }

        public int getCooldownMinutes() {
            return cooldownMinutes;
        }

        public void setCooldownMinutes(int cooldownMinutes) {
            this.cooldownMinutes = cooldownMinutes;
        }

        public int getLookbackMinutes() {
            return lookbackMinutes;
        }

        public void setLookbackMinutes(int lookbackMinutes) {
            this.lookbackMinutes = lookbackMinutes;
        }

        public int getMinimumEligibleSessions() {
            return minimumEligibleSessions;
        }

        public void setMinimumEligibleSessions(int minimumEligibleSessions) {
            this.minimumEligibleSessions = minimumEligibleSessions;
        }

        public int getMinimumUserMessages() {
            return minimumUserMessages;
        }

        public void setMinimumUserMessages(int minimumUserMessages) {
            this.minimumUserMessages = minimumUserMessages;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(List<String> recipients) {
            this.recipients = recipients;
        }
    }
}
