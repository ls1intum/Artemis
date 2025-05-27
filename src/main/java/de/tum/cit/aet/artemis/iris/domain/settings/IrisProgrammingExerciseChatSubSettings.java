package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH;

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
@DiscriminatorValue("PROGRAMMING_EXERCISE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisProgrammingExerciseChatSubSettings extends IrisSubSettings implements HasEnabledCategories {

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
    @Column(name = "custom_instructions", length = IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH)
    private String customInstructions;

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

    @Override
    public SortedSet<String> getEnabledForCategories() {
        return enabledForCategories;
    }

    @Override
    public void setEnabledForCategories(SortedSet<String> enabledForCategories) {
        this.enabledForCategories = enabledForCategories;
    }

    public SortedSet<String> getDisabledProactiveEvents() {
        return disabledProactiveEvents;
    }

    public void setDisabledProactiveEvents(SortedSet<String> disabledProactiveEvents) {
        this.disabledProactiveEvents = disabledProactiveEvents;
    }

    @Nullable
    public String getCustomInstructions() {
        return customInstructions;
    }

    public void setCustomInstructions(@Nullable String customInstructions) {
        this.customInstructions = customInstructions;
    }
}
