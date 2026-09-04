package de.tum.cit.aet.artemis.core.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * One day of counters for one feature and one caller role.
 * <p>
 * Deliberately narrow: the identifying strings live once in {@link TrackedFeature} and are referenced here by id, so a
 * row is a handful of numbers. With roughly 2000 rows per day and the default 400 day retention the whole table stays
 * well under a million rows.
 * <p>
 * Latency is stored as a sum and a maximum rather than a histogram. Mean plus worst case is enough to spot a feature
 * that is used but slow, and percentiles are already available from the Prometheus histograms without any storage cost
 * here.
 */
@Entity
@Table(name = "feature_usage_daily")
public class FeatureUsageDaily extends DomainObject {

    @Column(name = "feature_id", nullable = false)
    private Long featureId;

    @Column(name = "usage_day", nullable = false)
    private LocalDate usageDay;

    /**
     * The caller's highest <i>global</i> authority, not their role in the course the request touched. A user who
     * instructs any course carries {@code INSTRUCTOR} globally, so this measures who the audience of a feature is, not
     * in which capacity a given call was made.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "caller_role", length = 32, nullable = false)
    private Role callerRole;

    @Column(name = "call_count", nullable = false)
    private long callCount;

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    @Column(name = "duration_sum_ms", nullable = false)
    private long durationSumMs;

    @Column(name = "duration_max_ms", nullable = false)
    private int durationMaxMs;

    public FeatureUsageDaily() {
        // needed by Hibernate
    }

    public FeatureUsageDaily(Long featureId, LocalDate usageDay, Role callerRole, long callCount, long errorCount, long durationSumMs, int durationMaxMs) {
        this.featureId = featureId;
        this.usageDay = usageDay;
        this.callerRole = callerRole;
        this.callCount = callCount;
        this.errorCount = errorCount;
        this.durationSumMs = durationSumMs;
        this.durationMaxMs = durationMaxMs;
    }

    public Long getFeatureId() {
        return featureId;
    }

    public void setFeatureId(Long featureId) {
        this.featureId = featureId;
    }

    public LocalDate getUsageDay() {
        return usageDay;
    }

    public void setUsageDay(LocalDate usageDay) {
        this.usageDay = usageDay;
    }

    public Role getCallerRole() {
        return callerRole;
    }

    public void setCallerRole(Role callerRole) {
        this.callerRole = callerRole;
    }

    public long getCallCount() {
        return callCount;
    }

    public void setCallCount(long callCount) {
        this.callCount = callCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public long getDurationSumMs() {
        return durationSumMs;
    }

    public void setDurationSumMs(long durationSumMs) {
        this.durationSumMs = durationSumMs;
    }

    public int getDurationMaxMs() {
        return durationMaxMs;
    }

    public void setDurationMaxMs(int durationMaxMs) {
        this.durationMaxMs = durationMaxMs;
    }
}
