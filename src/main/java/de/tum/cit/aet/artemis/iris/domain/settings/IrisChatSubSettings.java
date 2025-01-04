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

    @Column(name = "disabled_proactive_events", nullable = false)
    @Convert(converter = IrisListConverter.class)
    private SortedSet<String> disabledProactiveEvents = new TreeSet<>();

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

    public SortedSet<String> getDisabledProactiveEvents() {
        return disabledProactiveEvents;
    }

    public void setDisabledProactiveEvents(SortedSet<String> disabledProactiveEvents) {
        this.disabledProactiveEvents = disabledProactiveEvents;
    }
}
