package de.tum.cit.aet.artemis.iris.config;

import java.time.Duration;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Operator-facing settings of the proactive struggle intervention.
 *
 * <p>
 * Deliberately not restricted to a profile. The cleanup job that reads {@link #getAbandonedEpisodeRetention()} runs
 * under {@code PROFILE_SCHEDULING} while the request-path readers run under {@code PROFILE_CORE}, so a bean profiled
 * for either one would be missing on a node that only has the other.
 */
@Configuration
@Lazy(false)
@Conditional(IrisEnabled.class)
@ConfigurationProperties(prefix = "artemis.iris.proactive")
public class IrisProactiveProperties {

    /**
     * How long an episode survives without a trigger before the nightly cleanup removes it. Only episodes that
     * reached no terminal outcome and carry no revealed offer are eligible; everything else goes with the course's
     * student-data reset. Every trigger refreshes {@code lastTriggeredAt}, so an episode still in use is never
     * reaped out from under a run in flight.
     */
    private Duration abandonedEpisodeRetention = Duration.ofDays(7);

    /**
     * When the cleanup runs. Kept here so the value is discoverable next to the retention it applies; the schedule
     * itself is bound by a placeholder on the {@code @Scheduled} method, which cannot read a bean getter.
     */
    private String cleanupCron = "0 30 3 * * *";

    /**
     * A USER reply counts as engagement with a proactive hint only if it follows the hint within this window.
     * Bounds how far apart a hint and a reply may sit before the reply is more plausibly about something else.
     */
    private Duration engagedReplyWindow = Duration.ofMinutes(10);

    /**
     * How often the persistence of a proactive message is retried after a transient failure.
     */
    private int persistMaxAttempts = 3;

    /**
     * Global kill switch for the legacy proactive triggers (a failed build, a stalled progress trajectory). A course
     * can still opt out on its own; this switch turns them off everywhere regardless of what a course decided.
     */
    private boolean legacyBuildTriggers = true;

    private final Struggle struggle = new Struggle();

    /**
     * The Pyris job lifetime, bound here only to validate the retention against it. Owned by
     * {@code PyrisJobService}, which binds the same key.
     */
    @Value("${artemis.iris.jobs.timeout:300}")
    private int jobTimeoutSeconds;

    /**
     * Rejects a configuration that would let the cleanup reap an episode a run could still write to.
     * <p>
     * The retention has to stay far above the Pyris job lifetime, because that lifetime is how long a job entry
     * survives an idle gap, and a callback whose job entry is gone is rejected anyway. A retention below it would
     * delete the row a still-answerable callback needs to lock, so the run would lose the terminal state it was
     * about to record. The factor of ten is a margin, not a proof: a non-terminal callback refreshes the job entry,
     * so a run that keeps reporting progress can outlive any single TTL. What bounds that case is
     * {@code lastTriggeredAt}, which every trigger refreshes, so only an episode nobody has triggered for the whole
     * retention window is ever eligible.
     */
    @PostConstruct
    public void validate() {
        if (jobTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("artemis.iris.jobs.timeout must be > 0, otherwise no Pyris run can outlive its own job entry");
        }
        if (abandonedEpisodeRetention == null || engagedReplyWindow == null) {
            throw new IllegalArgumentException("artemis.iris.proactive.abandoned-episode-retention and .engaged-reply-window must be set");
        }
        var jobTimeout = Duration.ofSeconds(jobTimeoutSeconds);
        if (abandonedEpisodeRetention.compareTo(jobTimeout.multipliedBy(10)) < 0) {
            throw new IllegalArgumentException("artemis.iris.proactive.abandoned-episode-retention (" + abandonedEpisodeRetention
                    + ") must be at least ten times artemis.iris.jobs.timeout (" + jobTimeout + "), otherwise the cleanup can reap an episode a Pyris callback still needs");
        }
        if (engagedReplyWindow.isNegative() || engagedReplyWindow.isZero()) {
            throw new IllegalArgumentException("artemis.iris.proactive.engaged-reply-window must be positive");
        }
        if (persistMaxAttempts < 1) {
            throw new IllegalArgumentException("artemis.iris.proactive.persist-max-attempts must be >= 1");
        }
        if (struggle.getConfidenceThreshold() < 0 || struggle.getConfidenceThreshold() > 1) {
            throw new IllegalArgumentException("artemis.iris.proactive.struggle.confidence-threshold must be within [0, 1]");
        }
    }

    public Duration getAbandonedEpisodeRetention() {
        return abandonedEpisodeRetention;
    }

    public void setAbandonedEpisodeRetention(Duration abandonedEpisodeRetention) {
        this.abandonedEpisodeRetention = abandonedEpisodeRetention;
    }

    public String getCleanupCron() {
        return cleanupCron;
    }

    public void setCleanupCron(String cleanupCron) {
        this.cleanupCron = cleanupCron;
    }

    public Duration getEngagedReplyWindow() {
        return engagedReplyWindow;
    }

    public void setEngagedReplyWindow(Duration engagedReplyWindow) {
        this.engagedReplyWindow = engagedReplyWindow;
    }

    public int getPersistMaxAttempts() {
        return persistMaxAttempts;
    }

    public void setPersistMaxAttempts(int persistMaxAttempts) {
        this.persistMaxAttempts = persistMaxAttempts;
    }

    public boolean isLegacyBuildTriggers() {
        return legacyBuildTriggers;
    }

    public void setLegacyBuildTriggers(boolean legacyBuildTriggers) {
        this.legacyBuildTriggers = legacyBuildTriggers;
    }

    public Struggle getStruggle() {
        return struggle;
    }

    /** Settings specific to the struggle-intervention pipeline, kept nested so the existing property key is unchanged. */
    public static class Struggle {

        /**
         * The confidence Pyris has to report before an unsolicited decision is delivered. A decision below it is
         * downgraded to silent; a consented help request bypasses the gate entirely.
         */
        private double confidenceThreshold = 0.6;

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }
    }
}
