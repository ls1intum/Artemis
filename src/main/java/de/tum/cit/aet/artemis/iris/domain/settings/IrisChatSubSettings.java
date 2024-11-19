package de.tum.cit.aet.artemis.iris.domain.settings;

import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSubSettings} implementation for chat settings.
 * Chat settings notably provide settings for the rate limit.
 */
@Entity
@DiscriminatorValue("CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisChatSubSettings extends IrisSubSettings {

    @Nullable
    @Column(name = "rate_limit")
    private Integer rateLimit;

    @Nullable
    @Column(name = "rate_limit_timeframe_hours")
    private Integer rateLimitTimeframeHours;

    @Column(name = "enabled_for_categories")
    @Convert(converter = IrisListConverter.class)
    private SortedSet<String> enabledForCategories = new TreeSet<>();

    @Column(name = "proactive_build_failed_event_enabled")
    private boolean proactiveBuildFailedEventEnabled;

    @Column(name = "proactive_progress_stalled_event_enabled")
    private boolean proactiveProgressStalledEventEnabled;

    @Nullable
    public Integer getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(@Nullable Integer rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Nullable
    public Integer getRateLimitTimeframeHours() {
        return rateLimitTimeframeHours;
    }

    public void setRateLimitTimeframeHours(@Nullable Integer rateLimitTimeframeHours) {
        this.rateLimitTimeframeHours = rateLimitTimeframeHours;
    }

    public SortedSet<String> getEnabledForCategories() {
        return enabledForCategories;
    }

    public void setEnabledForCategories(SortedSet<String> enabledForCategories) {
        this.enabledForCategories = enabledForCategories;
    }

    public boolean isProactiveProgressStalledEventEnabled() {
        return proactiveProgressStalledEventEnabled;
    }

    public void setProactiveProgressStalledEventEnabled(boolean proactiveProgressStalledEventEnabled) {
        this.proactiveProgressStalledEventEnabled = proactiveProgressStalledEventEnabled;
    }

    public boolean isProactiveBuildFailedEventEnabled() {
        return proactiveBuildFailedEventEnabled;
    }

    public void setProactiveBuildFailedEventEnabled(boolean proactiveBuildFailedEventEnabled) {
        this.proactiveBuildFailedEventEnabled = proactiveBuildFailedEventEnabled;
    }
}
