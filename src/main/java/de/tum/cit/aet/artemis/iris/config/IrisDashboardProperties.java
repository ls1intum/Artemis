package de.tum.cit.aet.artemis.iris.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    public void setDigest(Digest digest) {
        this.digest = digest;
    }

    public Alert getAlert() {
        return alert;
    }

    public void setAlert(Alert alert) {
        this.alert = alert;
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

        public int getMinimumActiveSessions() {
            return minimumActiveSessions;
        }

        public void setMinimumActiveSessions(int minimumActiveSessions) {
            this.minimumActiveSessions = minimumActiveSessions;
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
