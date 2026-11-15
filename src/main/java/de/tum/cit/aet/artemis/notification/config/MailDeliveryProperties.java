package de.tum.cit.aet.artemis.notification.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties of the e-mail delivery, bound from {@code artemis.mail.*}.
 * <p>
 * They configure how a mail that could not be handed to the SMTP server is kept and tried again (see
 * {@code MailRetryQueueService}), which protects the one SMTP account that every server of a deployment shares.
 * <p>
 * The sizes, batch sizes and durations must be positive, and the binding is {@code @Validated}, so a value that would
 * silently break the delivery fails the startup instead: a queue size of {@code 0} drops every mail, a batch size of
 * {@code 0} never works the queue off, and a delay of {@code 0} makes a failed mail due at once, again and again.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
@Validated
@ConfigurationProperties(prefix = "artemis.mail")
public class MailDeliveryProperties {

    @Valid
    private final Retry retry = new Retry();

    public Retry getRetry() {
        return retry;
    }

    /**
     * How a mail that the SMTP server did not accept is kept for another attempt.
     * <p>
     * The queue lives in the memory of the node that tried to send the mail, so it does not survive a restart. A restart
     * of a production node is a deployment, which is rare, and the mails of an outage are worth keeping across the
     * outage rather than across a deployment.
     */
    public static class Retry {

        /**
         * Whether a mail the SMTP server rejected for a temporary reason is queued and tried again. When disabled, such a
         * mail is logged and dropped.
         */
        private boolean enabled = true;

        /**
         * The most mails one node holds for another attempt. A mail that finds the queue full is dropped, except that a
         * transactional mail (a password reset) makes room by evicting the oldest bulk mail (a course announcement).
         * Every queued mail holds its rendered body, so this also bounds the memory the queue can take: a few thousand
         * mails are a few tens of megabytes.
         */
        @Positive
        private int maxQueueSize = 5000;

        /**
         * The wait before the second attempt. It doubles with every further failed attempt up to {@link #maxDelay}.
         */
        @DurationMin(nanos = 1)
        private Duration initialDelay = Duration.ofMinutes(1);

        /**
         * The longest wait between two attempts for the same mail.
         */
        @DurationMin(nanos = 1)
        private Duration maxDelay = Duration.ofHours(1);

        /**
         * How long a node stops contacting the SMTP server after it reported that the quota is used up. Mails that arrive
         * in the meantime go straight into the queue. Every attempt that is rejected for the quota is wasted work, and the
         * quota of a rolling window does not recover within minutes, so probing rarely is the right trade-off.
         */
        @DurationMin(nanos = 1)
        private Duration quotaPause = Duration.ofMinutes(15);

        /**
         * How long a node stops contacting the SMTP server after the server could not be reached or refused the
         * credentials.
         */
        @DurationMin(nanos = 1)
        private Duration unavailablePause = Duration.ofMinutes(2);

        /**
         * How long a transactional mail (password reset, activation, security notifications) stays worth sending. A mail
         * older than this is dropped instead of delivered, because its content is no longer true or its link no longer works.
         */
        @DurationMin(nanos = 1)
        private Duration transactionalMaxAge = Duration.ofHours(12);

        /**
         * How long a bulk mail (a notification of a course) stays worth sending.
         */
        @DurationMin(nanos = 1)
        private Duration bulkMaxAge = Duration.ofHours(48);

        /**
         * The pause between two runs of the job that works off the queue. A run ends early while the node is paused.
         */
        @DurationMin(nanos = 1)
        private Duration drainInterval = Duration.ofSeconds(30);

        /**
         * The most mails one run of the job sends. Together with {@link #drainInterval} this paces a backlog so that
         * working it off does not use up the quota again within minutes.
         */
        @Positive
        private int drainBatchSize = 200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxQueueSize() {
            return maxQueueSize;
        }

        public void setMaxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }

        public Duration getQuotaPause() {
            return quotaPause;
        }

        public void setQuotaPause(Duration quotaPause) {
            this.quotaPause = quotaPause;
        }

        public Duration getUnavailablePause() {
            return unavailablePause;
        }

        public void setUnavailablePause(Duration unavailablePause) {
            this.unavailablePause = unavailablePause;
        }

        public Duration getTransactionalMaxAge() {
            return transactionalMaxAge;
        }

        public void setTransactionalMaxAge(Duration transactionalMaxAge) {
            this.transactionalMaxAge = transactionalMaxAge;
        }

        public Duration getBulkMaxAge() {
            return bulkMaxAge;
        }

        public void setBulkMaxAge(Duration bulkMaxAge) {
            this.bulkMaxAge = bulkMaxAge;
        }

        public Duration getDrainInterval() {
            return drainInterval;
        }

        public void setDrainInterval(Duration drainInterval) {
            this.drainInterval = drainInterval;
        }

        public int getDrainBatchSize() {
            return drainBatchSize;
        }

        public void setDrainBatchSize(int drainBatchSize) {
            this.drainBatchSize = drainBatchSize;
        }
    }
}
