package de.tum.cit.aet.artemis.iris.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "artemis.iris.dashboard")
public class IrisDashboardProperties {

    private int maxQueryWindowDays = 365;

    private int staleThresholdMinutes = 5;

    private Digest digest = new Digest();

    private Alert alert = new Alert();

    public int getMaxQueryWindowDays() {
        return maxQueryWindowDays;
    }

    public void setMaxQueryWindowDays(int maxQueryWindowDays) {
        Assert.isTrue(maxQueryWindowDays >= 1, "maxQueryWindowDays must be >= 1");
        this.maxQueryWindowDays = maxQueryWindowDays;
    }

    public int getStaleThresholdMinutes() {
        return staleThresholdMinutes;
    }

    public void setStaleThresholdMinutes(int staleThresholdMinutes) {
        Assert.isTrue(staleThresholdMinutes >= 1, "staleThresholdMinutes must be >= 1");
        this.staleThresholdMinutes = staleThresholdMinutes;
    }

    public Digest getDigest() {
        return digest;
    }

    public void setDigest(Digest digest) {
        this.digest = Objects.requireNonNull(digest, "digest must not be null");
    }

    public Alert getAlert() {
        return alert;
    }

    public void setAlert(Alert alert) {
        this.alert = Objects.requireNonNull(alert, "alert must not be null");
    }

    public static class Digest {

        private boolean enabled;

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

        /**
         * Set the cron expression and fail fast if it cannot be parsed.
         *
         * @param cron the cron expression for the digest schedule
         */
        public void setCron(String cron) {
            Assert.hasText(cron, "cron must not be blank");
            CronExpression.parse(cron);
            this.cron = cron;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(List<String> recipients) {
            this.recipients = new ArrayList<>(List.copyOf(Objects.requireNonNull(recipients, "recipients must not be null")));
        }
    }

    public static class Alert {

        private boolean enabled;

        private double noResponseRateThreshold = 10.0;

        private int checkIntervalMinutes = 30;

        private int cooldownMinutes = 360;

        private int lookbackMinutes = 60;

        private int minimumActiveSessions = 10;

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
            Assert.isTrue(noResponseRateThreshold >= 0 && noResponseRateThreshold <= 100, "noResponseRateThreshold must be between 0 and 100");
            this.noResponseRateThreshold = noResponseRateThreshold;
        }

        public int getCheckIntervalMinutes() {
            return checkIntervalMinutes;
        }

        public void setCheckIntervalMinutes(int checkIntervalMinutes) {
            Assert.isTrue(checkIntervalMinutes >= 1, "checkIntervalMinutes must be >= 1");
            this.checkIntervalMinutes = checkIntervalMinutes;
        }

        public int getCooldownMinutes() {
            return cooldownMinutes;
        }

        public void setCooldownMinutes(int cooldownMinutes) {
            Assert.isTrue(cooldownMinutes >= 1, "cooldownMinutes must be >= 1");
            this.cooldownMinutes = cooldownMinutes;
        }

        public int getLookbackMinutes() {
            return lookbackMinutes;
        }

        public void setLookbackMinutes(int lookbackMinutes) {
            Assert.isTrue(lookbackMinutes >= 1, "lookbackMinutes must be >= 1");
            this.lookbackMinutes = lookbackMinutes;
        }

        public int getMinimumActiveSessions() {
            return minimumActiveSessions;
        }

        public void setMinimumActiveSessions(int minimumActiveSessions) {
            Assert.isTrue(minimumActiveSessions >= 1, "minimumActiveSessions must be >= 1");
            this.minimumActiveSessions = minimumActiveSessions;
        }

        public int getMinimumUserMessages() {
            return minimumUserMessages;
        }

        public void setMinimumUserMessages(int minimumUserMessages) {
            Assert.isTrue(minimumUserMessages >= 1, "minimumUserMessages must be >= 1");
            this.minimumUserMessages = minimumUserMessages;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(List<String> recipients) {
            this.recipients = new ArrayList<>(List.copyOf(Objects.requireNonNull(recipients, "recipients must not be null")));
        }
    }
}
